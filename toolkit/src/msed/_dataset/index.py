"""``episodes.parquet``: one row per converted episode, written after the episode's files."""

from __future__ import annotations

import json
from typing import TYPE_CHECKING

import pyarrow as pa
import pyarrow.parquet as pq
from google.protobuf import json_format

from msed._version import __version__

if TYPE_CHECKING:
    from pathlib import Path

    from msed._episode.folder import Episode

INDEX = "episodes.parquet"
TICKS = "ticks"

Row = dict[str, object]

_SCHEMA = pa.schema(
    [
        ("episode_id", pa.string()),
        ("session_id", pa.string()),
        ("user_id", pa.string()),
        ("task_description", pa.string()),
        ("msed_version", pa.string()),
        ("minecraft_version", pa.string()),
        ("world_seed", pa.int64()),
        # UTC, stored without a zone: zone-aware columns need the tz database (tzdata) on Windows.
        ("start_time", pa.timestamp("ms")),
        ("end_time", pa.timestamp("ms")),
        ("num_ticks", pa.int64()),
        ("first_tick", pa.int64()),
        ("last_tick", pa.int64()),
        ("ticks_file", pa.string()),
        ("world_video", pa.string()),
        ("gui_video", pa.string()),
        ("vocabulary_file", pa.string()),
        ("recording_config", pa.string()),
        ("msed_toolkit_version", pa.string()),
    ]
)


def episode_row(
    episode: Episode, ticks: tuple[int, int | None, int | None], assets: dict[str, str | None]
) -> Row:
    """Build the index row of one converted episode.

    Args:
        episode: The episode.
        ticks: Ticks written, and the first and last tick index.
        assets: Dataset-relative paths of the vocabulary and the videos.

    Returns:
        The row, in the columns of ``episodes.parquet``.
    """
    metadata = episode.metadata
    count, first, last = ticks
    return {
        "episode_id": episode.id,
        "session_id": metadata.session_id,
        "user_id": metadata.user_id,
        "task_description": metadata.task_description or None,
        "msed_version": metadata.msed_version,
        "minecraft_version": metadata.minecraft_version,
        "world_seed": metadata.world_seed if metadata.HasField("world_seed") else None,
        "start_time": metadata.start_time.ToDatetime() if metadata.HasField("start_time") else None,
        "end_time": metadata.end_time.ToDatetime() if metadata.HasField("end_time") else None,
        "num_ticks": count,
        "first_tick": first,
        "last_tick": last,
        "ticks_file": f"{TICKS}/{episode.id}.parquet",
        **assets,
        "recording_config": json.dumps(
            json_format.MessageToDict(metadata.recording_config, preserving_proto_field_name=True)
        ),
        "msed_toolkit_version": __version__,
    }


def converted_ids(dataset: Path) -> set[str]:
    """Return the ids of the episodes the dataset holds completely: an index row and a tick file."""
    index = dataset / INDEX
    if not index.is_file():
        return set()
    ids = pq.read_table(index, columns=["episode_id"]).column("episode_id").to_pylist()
    return {i for i in ids if (dataset / TICKS / f"{i}.parquet").is_file()}


def merge_row(dataset: Path, row: Row) -> None:
    """Add or replace one episode's row; the index file is replaced in one step."""
    index = dataset / INDEX
    rows: list[Row] = []
    if index.is_file():
        rows = [r for r in pq.read_table(index).to_pylist() if r["episode_id"] != row["episode_id"]]
    rows.append(row)
    rows.sort(key=lambda r: str(r["episode_id"]))
    partial = index.with_suffix(".parquet.partial")
    pq.write_table(pa.Table.from_pylist(rows, schema=_SCHEMA), partial)
    partial.replace(index)
