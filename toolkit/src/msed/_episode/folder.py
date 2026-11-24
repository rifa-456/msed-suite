"""Raw episodes as the mod writes them: the manifest, the shards and the videos."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from google.protobuf import json_format
from packaging.version import InvalidVersion, Version

from msed._episode.shards import read_shard
from msed._version import __version__
from msed.errors import EpisodeError
from msed.v1.metadata_pb2 import EpisodeMetadata, ShardInfo

if TYPE_CHECKING:
    from collections.abc import Iterator

    from msed.v1.tick_pb2 import TickData

METADATA_FILE = "metadata.json"
VOCABULARY_FILE = "vocabulary.json"
SHARD_FILES = "shard_*.bin"
# The manifest names files inside the episode folder and the episode itself; a name with a path
# separator or a leading dot could point outside the folder or the dataset.
_FILE_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*")


@dataclass(frozen=True, slots=True)
class Episode:
    """One recording session on disk.

    An episode folder holds ``metadata.json`` (the manifest), ``vocabulary.json``, the shards
    ``shard_0000.bin``, ``shard_0001.bin``, ... of length-delimited tick records, and the
    optional videos ``world.mp4`` and ``gui.mp4``.

    Attributes:
        path: The episode folder.
        metadata: The manifest.
    """

    path: Path
    metadata: EpisodeMetadata

    @classmethod
    def open(cls, path: str | Path) -> Episode:
        """Read an episode's manifest.

        Args:
            path: The episode folder.

        Returns:
            The episode.

        Raises:
            EpisodeError: The manifest is missing or invalid, names a file outside the folder, or
                the episode was written by an MSED major version this toolkit does not read.
        """
        folder = Path(path)
        manifest = folder / METADATA_FILE
        if not manifest.is_file():
            if any(folder.glob(SHARD_FILES)):
                raise EpisodeError(
                    f"{folder}: missing {METADATA_FILE}; the recording did not finish, "
                    "for example because the game closed or crashed"
                )
            raise EpisodeError(f"{folder}: missing {METADATA_FILE}")
        metadata = EpisodeMetadata()
        try:
            json_format.Parse(manifest.read_text(encoding="utf-8"), metadata)
        except json_format.ParseError as e:
            raise EpisodeError(f"{manifest}: invalid metadata ({e})") from e
        _check_compatible(manifest, metadata.msed_version)
        _check_names(manifest, metadata)
        return cls(path=folder, metadata=metadata)

    @property
    def id(self) -> str:
        """The episode id the mod assigned."""
        return self.metadata.episode_id

    @property
    def shards(self) -> list[ShardInfo]:
        """The shards in recording order, as the manifest lists them."""
        return list(self.metadata.shards)

    @property
    def vocabulary_path(self) -> Path:
        """Where the episode's ``vocabulary.json`` is."""
        return self.path / VOCABULARY_FILE

    @property
    def world_video(self) -> Path | None:
        """The world-only video; ``None`` when it was not recorded."""
        return self._video(self.metadata.world_video_path)

    @property
    def gui_video(self) -> Path | None:
        """The GUI-composited video; ``None`` when it was not recorded."""
        return self._video(self.metadata.gui_video_path)

    def iter_ticks(self) -> Iterator[TickData]:
        """Read every tick of the episode, across all shards, in order.

        Yields:
            The ticks.
        """
        for shard in self.metadata.shards:
            yield from read_shard(self.path / shard.file)

    def _video(self, name: str) -> Path | None:
        return self.path / name if name else None


def find_episodes(root: str | Path) -> list[Path]:
    """List the episode folders under ``root``, or ``root`` itself when it is one.

    A folder with shards but no ``metadata.json`` is listed too: it is a recording that did not
    finish, and opening it reports why.

    Args:
        root: An episode folder or a folder of episodes.

    Returns:
        The episode folders, sorted.

    Raises:
        EpisodeError: ``root`` is not a folder.
    """
    folder = Path(root)
    if _is_episode(folder):
        return [folder]
    if not folder.is_dir():
        raise EpisodeError(f"{folder}: not a directory")
    return sorted(p for p in folder.iterdir() if _is_episode(p))


def _is_episode(folder: Path) -> bool:
    return (folder / METADATA_FILE).is_file() or any(folder.glob(SHARD_FILES))


def _check_names(manifest: Path, metadata: EpisodeMetadata) -> None:
    names = [metadata.episode_id, *(shard.file for shard in metadata.shards)]
    names += [name for name in (metadata.world_video_path, metadata.gui_video_path) if name]
    for name in names:
        if not _FILE_NAME.fullmatch(name):
            raise EpisodeError(f"{manifest}: {name!r} is not a plain file name")


def _check_compatible(manifest: Path, written_by: str) -> None:
    # SemVer: the same major version reads and writes the same format.
    try:
        episode = Version(written_by)
    except InvalidVersion as e:
        raise EpisodeError(f"{manifest}: invalid msed_version {written_by!r}") from e
    toolkit = Version(__version__)
    if episode.major != toolkit.major:
        raise EpisodeError(
            f"{manifest}: written by MSED {episode}; this toolkit reads {toolkit.major}.x"
        )
