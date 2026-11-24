# The docs hooks import this file from their own folder; it is not part of a package.
"""The protobuf schema in ``proto/``, compiled with its comments and custom field options."""

from __future__ import annotations

import functools
import struct
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

from google.protobuf import descriptor_pb2

PROTO_ROOT = Path(__file__).resolve().parents[2] / "proto"
PACKAGE = ".msed.v1."

FDP = descriptor_pb2.FieldDescriptorProto
# Field numbers inside FileDescriptorProto and DescriptorProto, as SourceCodeInfo paths use them.
FILE_MESSAGES, FILE_ENUMS = 4, 5
MESSAGE_FIELDS, MESSAGE_NESTED, MESSAGE_ENUMS = 2, 3, 4
ENUM_VALUES = 2
# Protobuf wire types: a varint, a length-delimited value, and the byte size of the fixed ones.
WIRE_VARINT, WIRE_LEN = 0, 2
WIRE_FIXED_SIZE = {1: 8, 5: 4}
INT64_RANGE = 1 << 64


@dataclass
class Message:
    """A message of the schema and where it is in its file."""

    name: str
    proto: descriptor_pb2.DescriptorProto
    path: tuple[int, ...]
    file: str


@dataclass
class Enum:
    """An enum of the schema and where it is in its file."""

    name: str
    proto: descriptor_pb2.EnumDescriptorProto
    path: tuple[int, ...]
    file: str


class Schema:
    """The compiled schema, with the comment of each element by its source path."""

    def __init__(self) -> None:
        self.messages: dict[str, Message] = {}
        self.enums: dict[str, Enum] = {}
        self.comments: dict[tuple[str, tuple[int, ...]], str] = {}
        self.extensions: dict[str, int] = {}
        self.order: list[str] = []
        for file in _compile():
            if not file.name.startswith("msed/"):
                continue
            for location in file.source_code_info.location:
                text = (location.leading_comments or location.trailing_comments).strip()
                if text:
                    self.comments[(file.name, tuple(location.path))] = " ".join(
                        line.strip() for line in text.splitlines()
                    )
            self.extensions.update({ext.name: ext.number for ext in file.extension})
            for i, message in enumerate(file.message_type):
                self._add_message(file.name, message, f".{file.package}", (FILE_MESSAGES, i))
            for i, enum in enumerate(file.enum_type):
                full = f".{file.package}.{enum.name}"
                self.enums[full] = Enum(enum.name, enum, (FILE_ENUMS, i), file.name)
                self.order.append(full)

    def _add_message(
        self, file: str, message: descriptor_pb2.DescriptorProto, scope: str, path: tuple[int, ...]
    ) -> None:
        full = f"{scope}.{message.name}"
        if message.options.map_entry:
            self.messages[full] = Message(message.name, message, path, file)
            return
        self.messages[full] = Message(full.removeprefix(PACKAGE), message, path, file)
        self.order.append(full)
        for i, nested in enumerate(message.nested_type):
            self._add_message(file, nested, full, (*path, MESSAGE_NESTED, i))
        for i, enum in enumerate(message.enum_type):
            nested_full = f"{full}.{enum.name}"
            self.enums[nested_full] = Enum(
                nested_full.removeprefix(PACKAGE), enum, (*path, MESSAGE_ENUMS, i), file
            )
            self.order.append(nested_full)

    def comment(self, file: str, path: tuple[int, ...]) -> str:
        """Return the comment of the element at a source path, or an empty string."""
        return self.comments.get((file, path), "")

    def option(self, field: descriptor_pb2.FieldDescriptorProto, name: str) -> int | bytes | None:
        """Return a custom option of a field by its name in ``options.proto``, or ``None``.

        protoc keeps the custom options as unknown fields of the field's options, so a varint
        option comes back as a signed int and any other as its raw bytes.
        """
        return _unknown(field.options.SerializeToString()).get(self.extensions[name])

    def int_option(self, field: descriptor_pb2.FieldDescriptorProto, name: str) -> int | None:
        """Return an int or bool option of a field, or ``None``."""
        value = self.option(field, name)
        return value if isinstance(value, int) else None

    def float_option(self, field: descriptor_pb2.FieldDescriptorProto, name: str) -> float | None:
        """Return a float option of a field, or ``None``."""
        value = self.option(field, name)
        return struct.unpack("<f", value)[0] if isinstance(value, bytes) else None

    def string_option(self, field: descriptor_pb2.FieldDescriptorProto, name: str) -> str | None:
        """Return a string option of a field, or ``None``."""
        value = self.option(field, name)
        return value.decode() if isinstance(value, bytes) else None


@functools.cache
def load() -> Schema:
    """Return the schema, compiled once per build."""
    return Schema()


def anchor(name: str) -> str:
    """Return the heading anchor of a message or enum name."""
    return name.lower().replace(".", "-")


def _unknown(raw: bytes) -> dict[int, int | bytes]:
    out: dict[int, int | bytes] = {}
    pos = 0
    while pos < len(raw):
        key, pos = _varint(raw, pos)
        number, wire = key >> 3, key & 7
        if wire == WIRE_VARINT:
            value, pos = _varint(raw, pos)
            out[number] = value - INT64_RANGE if value >= INT64_RANGE >> 1 else value
        elif wire == WIRE_LEN:
            length, pos = _varint(raw, pos)
            out[number] = raw[pos : pos + length]
            pos += length
        elif wire in WIRE_FIXED_SIZE:
            out[number] = raw[pos : pos + WIRE_FIXED_SIZE[wire]]
            pos += WIRE_FIXED_SIZE[wire]
        else:
            break
    return out


def _varint(raw: bytes, pos: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte = raw[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, pos
        shift += 7


def _compile() -> list[descriptor_pb2.FileDescriptorProto]:
    import grpc_tools  # noqa: PLC0415 - only the docs build needs protoc

    include = Path(grpc_tools.__file__).parent / "_proto"
    files = sorted(p.relative_to(PROTO_ROOT).as_posix() for p in PROTO_ROOT.rglob("*.proto"))
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "schema.pb"
        subprocess.run(
            [
                sys.executable,
                "-m",
                "grpc_tools.protoc",
                f"-I{PROTO_ROOT.as_posix()}",
                f"-I{include.as_posix()}",
                "--include_source_info",
                "--include_imports",
                f"--descriptor_set_out={out.as_posix()}",
                *files,
            ],
            check=True,
        )
        result = descriptor_pb2.FileDescriptorSet.FromString(out.read_bytes())
    return list(result.file)
