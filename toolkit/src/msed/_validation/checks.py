"""Checks of a raw episode for the problems a recording can leave behind.

Errors mean the data cannot be trusted: a missing or truncated shard, a shard whose size or
SHA-256 differs from the manifest, an unreadable record, ticks going backwards, a frame index past
the end of its video. Warnings mean the data is valid but degraded: dropped ticks, ticks whose
frame repeats the previous one, a recording that ended without its stop event.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import TYPE_CHECKING

from google.protobuf.message import DecodeError

from msed._episode.folder import Episode
from msed._episode.shards import read_shard
from msed._episode.video import video_frame_count
from msed._validation.report import Report
from msed.errors import EpisodeError

if TYPE_CHECKING:
    from msed.v1.metadata_pb2 import ShardInfo
    from msed.v1.tick_pb2 import TickData

_HASH_CHUNK = 1 << 20
_LISTED_GAPS = 5


def validate_episode(path: str | Path, *, check_videos: bool = True) -> Report:
    """Validate one raw episode.

    Args:
        path: The episode folder.
        check_videos: Also count the frames of each video, which needs FFmpeg on ``PATH``.

    Returns:
        Everything found; ``Report.ok`` is false when the episode has errors.
    """
    folder = Path(path)
    try:
        episode = Episode.open(folder)
    except EpisodeError as e:
        report = Report(episode_id=folder.name, path=folder)
        report.error(str(e))
        return report
    report = Report(episode_id=episode.id, path=folder)
    if not episode.shards:
        report.error("metadata lists no shards")
        return report
    _check_vocabulary(episode, report)
    scan = _TickScan(report)
    for shard in episode.shards:
        _check_shard(folder, shard, scan)
    scan.finish()
    _check_videos(episode, scan, check_videos=check_videos)
    return report


def _check_vocabulary(episode: Episode, report: Report) -> None:
    if not episode.vocabulary_path.is_file():
        report.error("missing vocabulary.json")
        return
    try:
        json.loads(episode.vocabulary_path.read_text(encoding="utf-8"))
    except ValueError as e:
        report.error(f"vocabulary.json is not valid JSON ({e})")


def _check_shard(folder: Path, shard: ShardInfo, scan: _TickScan) -> None:
    report = scan.report
    path = folder / shard.file
    if not path.is_file():
        report.error(f"{shard.file}: listed in metadata but missing")
        return
    size = path.stat().st_size
    if size != shard.size_bytes:
        report.error(f"{shard.file}: size {size} != {shard.size_bytes} in metadata (truncated?)")
    if not shard.sha256:
        report.error(f"{shard.file}: no SHA-256 in metadata")
    elif _sha256(path) != shard.sha256:
        report.error(f"{shard.file}: SHA-256 mismatch")
    read: list[int] = []
    try:
        for tick in read_shard(path):
            read.append(tick.tick)
            scan.add(tick)
    except EpisodeError as e:
        report.error(str(e))
    except DecodeError as e:
        report.error(f"{shard.file}: unreadable record ({e})")
    if read and (read[0], read[-1]) != (shard.first_tick, shard.last_tick):
        report.error(
            f"{shard.file}: contains ticks {read[0]}..{read[-1]}, "
            f"metadata says {shard.first_tick}..{shard.last_tick}"
        )


def _check_videos(episode: Episode, scan: _TickScan, *, check_videos: bool) -> None:
    for stream, video in (("world", episode.world_video), ("gui", episode.gui_video)):
        last_frame = scan.max_frame[stream]
        if video is None or last_frame < 0:
            continue
        if not video.is_file():
            scan.report.error(f"{video.name}: referenced by ticks but missing")
            continue
        frames = video_frame_count(video) if check_videos else None
        if frames is not None and last_frame >= frames:
            scan.report.error(
                f"{video.name}: ticks reference frame {last_frame} "
                f"but the video has {frames} frames"
            )


@dataclass(slots=True)
class _TickScan:
    """What the ticks of an episode show when read in order across shards."""

    report: Report
    previous: int | None = None
    gaps: list[int] = field(default_factory=list)
    max_frame: dict[str, int] = field(default_factory=lambda: {"world": -1, "gui": -1})
    # Ticks whose frame repeats the previous one: the mod writes them without a camera pose.
    repeated: list[int] = field(default_factory=list)
    last_events: list[str] = field(default_factory=list)

    def add(self, tick: TickData) -> None:
        index = tick.tick
        if self.previous is not None:
            if index <= self.previous:
                self.report.error(f"tick {index} follows tick {self.previous} (not increasing)")
            elif index != self.previous + 1:
                self.gaps.append(self.previous + 1)
        self.previous = index
        self.report.count_tick(index)
        if tick.HasField("vision"):
            if tick.vision.world_frame.file_name and not tick.vision.HasField("camera"):
                self.repeated.append(index)
            self._add_frame(
                "world",
                index,
                tick.vision.world_frame.file_name,
                tick.vision.world_frame.frame_index,
            )
            self._add_frame(
                "gui", index, tick.vision.gui_frame.file_name, tick.vision.gui_frame.frame_index
            )
        self.last_events = [event.WhichOneof("payload") or "" for event in tick.events]

    def finish(self) -> None:
        if self.gaps:
            shown = ", ".join(map(str, self.gaps[:_LISTED_GAPS]))
            more = len(self.gaps) - _LISTED_GAPS
            self.report.warn(
                f"{len(self.gaps)} dropped tick(s), first missing: {shown}"
                + (f" (+{more} more)" if more > 0 else "")
            )
        # The terminal tick repeats the last frame by design.
        repeated = [t for t in self.repeated if t != self.previous]
        if repeated:
            self.report.warn(
                f"{len(repeated)} tick(s) repeat the previous video frame, first: "
                + ", ".join(map(str, repeated[:_LISTED_GAPS]))
            )
        if self.report.ticks and "stopped" not in self.last_events:
            self.report.warn(
                "recording was interrupted: the last tick has no RecordingStopped event"
            )

    def _add_frame(self, stream: str, tick: int, file_name: str, frame: int) -> None:
        if not file_name:
            return
        if frame <= self.max_frame[stream]:
            self.report.error(f"tick {tick}: {stream} frame index does not increase")
        self.max_frame[stream] = max(self.max_frame[stream], frame)


def _sha256(path: Path) -> bytes:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(_HASH_CHUNK), b""):
            digest.update(chunk)
    return digest.digest()
