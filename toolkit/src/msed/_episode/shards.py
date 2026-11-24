"""Shard files: tick records in protobuf's length-delimited framing."""

from __future__ import annotations

from typing import TYPE_CHECKING

from msed.errors import EpisodeError
from msed.v1.tick_pb2 import TickData, TickRecord

if TYPE_CHECKING:
    from collections.abc import Iterator
    from pathlib import Path

_VARINT_PAYLOAD = 0x7F
_VARINT_CONTINUES = 0x80
_VARINT_MAX_SHIFT = 64


def read_shard(path: Path) -> Iterator[TickData]:
    """Read the ticks of one shard in recording order.

    Args:
        path: The shard file.

    Yields:
        The ticks.

    Raises:
        EpisodeError: The shard ends in the middle of a record.
    """
    data = path.read_bytes()
    pos = 0
    while pos < len(data):
        size, pos = _read_varint(data, pos)
        end = pos + size
        if end > len(data):
            raise EpisodeError(f"{path.name}: truncated record at byte {pos}")
        record = TickRecord()
        record.ParseFromString(data[pos:end])
        pos = end
        yield record.data


def _read_varint(data: bytes, pos: int) -> tuple[int, int]:
    # Base-128, little-endian groups of 7 bits; the high bit marks that more bytes follow.
    result = 0
    shift = 0
    while True:
        if pos >= len(data):
            raise EpisodeError("truncated varint (shard ends mid-record)")
        byte = data[pos]
        pos += 1
        result |= (byte & _VARINT_PAYLOAD) << shift
        if not byte & _VARINT_CONTINUES:
            return result, pos
        shift += 7
        if shift >= _VARINT_MAX_SHIFT:
            raise EpisodeError("malformed varint")
