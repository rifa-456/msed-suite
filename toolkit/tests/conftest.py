"""Build small synthetic episodes on disk, byte-compatible with what the mod writes."""

from __future__ import annotations

import hashlib
import json
from typing import TYPE_CHECKING

import pytest
from google.protobuf import json_format

from msed.v1.events_pb2 import Event, ItemPickedUpEvent, RecordingStoppedEvent
from msed.v1.metadata_pb2 import EpisodeMetadata, ShardInfo
from msed.v1.tick_pb2 import TickData, TickRecord

if TYPE_CHECKING:
    from collections.abc import Callable
    from pathlib import Path

VOCABULARY = {
    "blocks": {"entries": {"0": "minecraft:air", "1": "minecraft:stone"}},
    "items": {"entries": {"0": "minecraft:air", "7": "minecraft:wheat_seeds"}},
    "entities": {"entries": {}},
    "sounds": {"entries": {}},
    "screens": {"entries": {}},
    "semantic_tags": {"entries": {}},
    "events": {"entries": {}},
    "effects": {"entries": {}},
    "field_index": {"tick.events.item_picked_up.item_id": "VOCAB_ITEMS"},
}


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        byte = n & 0x7F
        n >>= 7
        if n:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def make_tick(t: int, *, stopped: bool = False) -> TickData:
    tick = TickData(tick=t, timestamp=1_700_000_000.0 + t / 20)
    tick.player.health = 20.0
    tick.player.position.x = float(t)
    tick.player.on_ground = t % 2 == 0
    tick.vision.world_frame.file_name = "world.mp4"
    tick.vision.world_frame.frame_index = t - 1
    tick.vision.gui_frame.file_name = "gui.mp4"
    tick.vision.gui_frame.frame_index = t - 1
    # A rendered tick carries its camera pose; the terminal tick repeats the last frame without one.
    if not stopped:
        tick.vision.camera.partial_tick = 0.5
    if t == 3:
        tick.events.append(Event(tick=t, item_picked_up=ItemPickedUpEvent(item_id=7, count=2)))
    if stopped:
        tick.events.append(Event(tick=t, stopped=RecordingStoppedEvent()))
    return tick


def write_episode(
    root: Path,
    *,
    episode_id: str = "ep1",
    ticks: list[int] | None = None,
    ticks_per_shard: int = 4,
    stop_event: bool = True,
    msed_version: str = "1.0.0",
) -> Path:
    """Write an episode the way the mod does: delimited shards + metadata + vocabulary."""
    ticks = ticks if ticks is not None else list(range(1, 11))
    path = root / episode_id
    path.mkdir(parents=True)
    meta = EpisodeMetadata(
        episode_id=episode_id,
        session_id="session",
        user_id="user",
        msed_version=msed_version,
        minecraft_version="1.21.11",
        world_seed=42,
        world_video_path="world.mp4",
        gui_video_path="gui.mp4",
    )
    meta.start_time.FromSeconds(1_700_000_000)
    meta.end_time.FromSeconds(1_700_000_010)
    for i in range(0, len(ticks), ticks_per_shard):
        chunk = ticks[i : i + ticks_per_shard]
        name = f"shard_{i // ticks_per_shard:04d}.bin"
        data = bytearray()
        for t in chunk:
            last = stop_event and t == ticks[-1]
            payload = TickRecord(data=make_tick(t, stopped=last)).SerializeToString()
            data += varint(len(payload)) + payload
        (path / name).write_bytes(bytes(data))
        meta.shards.append(
            ShardInfo(
                file=name,
                first_tick=chunk[0],
                last_tick=chunk[-1],
                size_bytes=len(data),
                sha256=hashlib.sha256(data).digest(),
            )
        )
    (path / "metadata.json").write_text(
        json_format.MessageToJson(meta, preserving_proto_field_name=True), encoding="utf-8"
    )
    (path / "vocabulary.json").write_text(json.dumps(VOCABULARY), encoding="utf-8")
    # Placeholder videos: tests never decode them (frame counting is skipped when it fails).
    for video in ("world.mp4", "gui.mp4"):
        (path / video).write_bytes(b"\x00\x00\x00\x08free")
    return path


@pytest.fixture
def episode_factory(tmp_path: Path) -> Callable[..., Path]:
    def factory(**kwargs) -> Path:
        return write_episode(tmp_path / "recordings", **kwargs)

    return factory
