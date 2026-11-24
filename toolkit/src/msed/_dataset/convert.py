"""Converts raw episodes into a Parquet dataset that uploads to Hugging Face as it is.

The dataset folder holds ``README.md`` (the dataset card), ``episodes.parquet`` (one row per
episode), ``ticks/<episode_id>.parquet`` (one row per tick) and ``episodes/<episode_id>/`` (the
manifest, the vocabulary and the videos). Conversion reads the files the mod wrote, after the
game, so it adds no work to a recording. Each episode is written completely before the next one
starts, so a run that stops midway resumes where it left off.
"""

from __future__ import annotations

import shutil
from dataclasses import dataclass, field
from importlib import resources
from pathlib import Path
from typing import TYPE_CHECKING

import pyarrow as pa
import pyarrow.parquet as pq
from google.protobuf.message import DecodeError
from tqdm import tqdm

from msed._dataset.columns import tick_row, tick_schema
from msed._dataset.index import TICKS, Row, converted_ids, episode_row, merge_row
from msed._episode.folder import METADATA_FILE, VOCABULARY_FILE, Episode, find_episodes
from msed._episode.shards import read_shard
from msed._validation.checks import validate_episode
from msed.errors import EpisodeError

if TYPE_CHECKING:
    from collections.abc import Iterator

    from msed._validation.report import Report
    from msed.v1.tick_pb2 import TickData

_BATCH_ROWS = 2_000
_EPISODES = "episodes"
_CARD = "README.md"


@dataclass(slots=True)
class ConvertResult:
    """What a conversion did with each episode; it accumulates while the run goes.

    Attributes:
        converted: Ids of the episodes converted in this run.
        with_errors: Reports of the episodes converted although validation found errors, which
            ``allow_errors`` permits. Each keeps the ticks that could be read.
        unchanged: Ids of the episodes already in the dataset, left as they were.
        skipped: Reports of the episodes left out: validation found errors, the episode could
            not be opened or converted, or another episode already had its id.
    """

    converted: list[str] = field(default_factory=list)
    with_errors: list[Report] = field(default_factory=list)
    unchanged: list[str] = field(default_factory=list)
    skipped: list[Report] = field(default_factory=list)


def convert(
    source: str | Path,
    output: str | Path,
    *,
    copy_videos: bool = True,
    overwrite: bool = False,
    allow_errors: bool = False,
) -> ConvertResult:
    """Validate and convert every episode under ``source`` into the dataset at ``output``.

    Each episode is validated in full first, video frame counts included. While an episode is
    converted, a progress bar of its ticks is drawn on stderr when stderr is a terminal.

    Args:
        source: An episode folder or a folder of episodes.
        output: The dataset folder; created when missing, added to when it exists.
        copy_videos: Copy the videos into the dataset.
        overwrite: Convert episodes already in the dataset again.
        allow_errors: Convert episodes whose validation found errors.

    Returns:
        What happened to each episode.
    """
    dataset = Path(output)
    (dataset / TICKS).mkdir(parents=True, exist_ok=True)
    done = converted_ids(dataset)
    seen: set[str] = set()
    result = ConvertResult()
    for folder in find_episodes(source):
        report = validate_episode(folder)
        if not report.ok and not allow_errors:
            result.skipped.append(report)
            continue
        try:
            episode = Episode.open(folder)
        except EpisodeError:
            # validate_episode has already reported why it cannot be opened.
            result.skipped.append(report)
            continue
        if episode.id in seen:
            report.error(f"episode id {episode.id} is also the id of another folder in this run")
            result.skipped.append(report)
            continue
        seen.add(episode.id)
        if episode.id in done and not overwrite:
            result.unchanged.append(episode.id)
            continue
        try:
            _convert_episode(episode, dataset, report, copy_videos=copy_videos)
        except (EpisodeError, DecodeError, OSError) as e:
            report.error(f"conversion failed: {e}")
            result.skipped.append(report)
            continue
        result.converted.append(episode.id)
        if not report.ok:
            result.with_errors.append(report)
    card = dataset / _CARD
    if not card.exists():
        card.write_text(
            resources.files("msed._dataset")
            .joinpath("templates/dataset_card.md")
            .read_text("utf-8"),
            "utf-8",
        )
    return result


def _convert_episode(episode: Episode, dataset: Path, report: Report, *, copy_videos: bool) -> None:
    # The index row is written last, so an episode is in the index only once all its files are.
    target = dataset / TICKS / f"{episode.id}.parquet"
    partial = target.with_suffix(".parquet.partial")
    try:
        count, first, last = _write_ticks(_readable_ticks(episode, report), episode, partial)
    except BaseException:
        partial.unlink(missing_ok=True)
        raise
    partial.replace(target)
    assets = _copy_assets(episode, dataset, copy_videos=copy_videos)
    merge_row(dataset, episode_row(episode, (count, first, last), assets))


def _readable_ticks(episode: Episode, report: Report) -> Iterator[TickData]:
    for shard in episode.shards:
        yield from _readable_shard(episode.path / shard.file, report)


def _readable_shard(path: Path, report: Report) -> Iterator[TickData]:
    # A shard is read up to its first unreadable record; the ticks before it are kept.
    try:
        yield from read_shard(path)
    except (EpisodeError, DecodeError, OSError) as e:
        report.warn(f"{path.name}: converted the ticks before an unreadable part ({e})")


def _write_ticks(
    ticks_in: Iterator[TickData], episode: Episode, target: Path
) -> tuple[int, int | None, int | None]:
    schema = tick_schema()
    rows: list[Row] = []
    count, first, last = 0, None, None
    with pq.ParquetWriter(target, schema, compression="zstd") as writer:
        ticks = tqdm(
            ticks_in,
            desc=episode.id,
            total=sum(shard.last_tick - shard.first_tick + 1 for shard in episode.shards),
            unit=" ticks",
            disable=None,
            leave=False,
        )
        for tick in ticks:
            rows.append(tick_row(tick, episode.id))
            count += 1
            first = tick.tick if first is None else first
            last = tick.tick
            if len(rows) >= _BATCH_ROWS:
                writer.write_table(pa.Table.from_pylist(rows, schema=schema))
                rows.clear()
        if rows:
            writer.write_table(pa.Table.from_pylist(rows, schema=schema))
    return count, first, last


def _copy_assets(episode: Episode, dataset: Path, *, copy_videos: bool) -> dict[str, str | None]:
    folder = dataset / _EPISODES / episode.id
    folder.mkdir(parents=True, exist_ok=True)
    relative = f"{_EPISODES}/{episode.id}"
    for name in (METADATA_FILE, VOCABULARY_FILE):
        source = episode.path / name
        if source.is_file():
            shutil.copy2(source, folder / name)
    paths: dict[str, str | None] = {"vocabulary_file": f"{relative}/{VOCABULARY_FILE}"}
    for key, video in (("world_video", episode.world_video), ("gui_video", episode.gui_video)):
        if copy_videos and video is not None and video.is_file():
            shutil.copy2(video, folder / video.name)
            paths[key] = f"{relative}/{video.name}"
        else:
            paths[key] = None
    return paths
