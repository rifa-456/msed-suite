from __future__ import annotations

import base64
import hashlib
import json
from typing import TYPE_CHECKING, Any

import pytest

from msed import Episode, EpisodeError, find_episodes, validate_episode
from msed._episode.shards import read_shard
from msed.v1.tick_pb2 import TickRecord
from tests.conftest import varint

if TYPE_CHECKING:
    from collections.abc import Callable
    from pathlib import Path


def test_reads_all_ticks_across_shards(episode_factory):
    ep = Episode.open(episode_factory())
    assert [t.tick for t in ep.iter_ticks()] == list(range(1, 11))
    assert len(ep.shards) == 3


def test_find_episodes_accepts_root_or_single_episode(episode_factory):
    ep1 = episode_factory(episode_id="a")
    episode_factory(episode_id="b")
    assert [p.name for p in find_episodes(ep1.parent)] == ["a", "b"]
    assert find_episodes(ep1) == [ep1]


def test_clean_episode_is_valid(episode_factory):
    report = validate_episode(episode_factory(), check_videos=False)
    assert report.ok, report.issues
    assert report.warnings == []
    assert (report.ticks, report.first_tick, report.last_tick) == (10, 1, 10)


def test_detects_corrupted_shard(episode_factory):
    path = episode_factory()
    shard = path / "shard_0001.bin"
    data = bytearray(shard.read_bytes())
    data[-1] ^= 0xFF
    shard.write_bytes(bytes(data))
    report = validate_episode(path, check_videos=False)
    assert not report.ok
    assert any("SHA-256" in e for e in report.errors)


def test_detects_truncated_shard(episode_factory):
    path = episode_factory()
    shard = path / "shard_0002.bin"
    shard.write_bytes(shard.read_bytes()[:-3])
    report = validate_episode(path, check_videos=False)
    assert any("size" in e for e in report.errors)
    assert any("truncated" in e for e in report.errors)


def test_detects_missing_shard_and_vocabulary(episode_factory):
    path = episode_factory()
    (path / "shard_0000.bin").unlink()
    (path / "vocabulary.json").unlink()
    report = validate_episode(path, check_videos=False)
    assert any("missing" in e and "shard_0000" in e for e in report.errors)
    assert any("vocabulary" in e for e in report.errors)


def test_dropped_ticks_are_a_warning(episode_factory):
    report = validate_episode(episode_factory(ticks=[1, 2, 3, 5, 6, 9]), check_videos=False)
    assert report.ok
    assert any("2 dropped tick(s)" in w and "4" in w for w in report.warnings)


def test_ticks_going_backwards_is_an_error(episode_factory):
    report = validate_episode(episode_factory(ticks=[1, 2, 3, 2, 4]), check_videos=False)
    assert any("not increasing" in e for e in report.errors)


def test_missing_stop_event_is_a_warning(episode_factory):
    report = validate_episode(episode_factory(stop_event=False), check_videos=False)
    assert report.ok
    assert any("RecordingStopped" in w for w in report.warnings)


def test_missing_metadata(tmp_path):
    (tmp_path / "broken").mkdir()
    report = validate_episode(tmp_path / "broken")
    assert not report.ok


def test_accepts_other_minor_version(episode_factory):
    assert validate_episode(episode_factory(msed_version="1.4.2"), check_videos=False).ok


def test_rejects_other_major_version(episode_factory):
    report = validate_episode(episode_factory(msed_version="2.0.0"), check_videos=False)
    assert any("written by MSED 2.0.0; this toolkit reads 1.x" in e for e in report.errors)


def test_rejects_invalid_version(episode_factory):
    report = validate_episode(episode_factory(msed_version=""), check_videos=False)
    assert any("invalid msed_version" in e for e in report.errors)


def _edit_manifest(path: Path, change: Callable[[dict[str, Any]], object]) -> None:
    manifest = path / "metadata.json"
    data = json.loads(manifest.read_text(encoding="utf-8"))
    change(data)
    manifest.write_text(json.dumps(data), encoding="utf-8")


def test_unfinished_recording_is_listed_and_reported(episode_factory):
    path = episode_factory()
    (path / "metadata.json").unlink()
    assert find_episodes(path.parent) == [path]
    report = validate_episode(path, check_videos=False)
    assert not report.ok
    assert "did not finish" in report.errors[0]


@pytest.mark.parametrize(
    "change",
    [
        lambda m: m.update(episode_id="../elsewhere"),
        lambda m: m["shards"][0].update(file="../shard_0000.bin"),
        lambda m: m.update(world_video_path="/videos/world.mp4"),
    ],
)
def test_manifest_names_outside_the_folder_are_rejected(episode_factory, change):
    path = episode_factory()
    _edit_manifest(path, change)
    with pytest.raises(EpisodeError, match="not a plain file name"):
        Episode.open(path)


def test_shard_without_a_digest_is_an_error(episode_factory):
    path = episode_factory()
    _edit_manifest(path, lambda m: m["shards"][0].pop("sha256"))
    report = validate_episode(path, check_videos=False)
    assert any("no SHA-256" in e for e in report.errors)


def test_ticks_without_a_render_are_reported_as_repeated_frames(episode_factory):
    path = episode_factory(ticks_per_shard=10)
    shard = path / "shard_0000.bin"
    records = list(read_shard(shard))
    records[4].vision.ClearField("camera")
    data = b"".join(
        varint(len(p)) + p for p in (TickRecord(data=t).SerializeToString() for t in records)
    )
    shard.write_bytes(data)
    digest = base64.b64encode(hashlib.sha256(data).digest()).decode()
    _edit_manifest(path, lambda m: m["shards"][0].update(size_bytes=str(len(data)), sha256=digest))
    report = validate_episode(path, check_videos=False)
    assert report.ok, report.errors
    assert any("1 tick(s) repeat the previous video frame, first: 5" in w for w in report.warnings)
