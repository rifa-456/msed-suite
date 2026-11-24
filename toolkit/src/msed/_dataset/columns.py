"""The Parquet (Arrow) schema of a tick, derived from the protobuf schema, and ticks as rows.

The schema is built from the ``TickData`` descriptor each time, so a field added to the
``.proto`` files appears in the next converted dataset without a change here. The layout:

* A nested message that is not repeated is flattened into dotted columns
  (``player.position.x``), so one modality can be read by selecting its columns.
* A repeated field is a list; a repeated message is a ``list<struct>``.
* Each ``oneof`` gets a string column or field naming the case that is set
  (``events[].payload == "item_picked_up"``).
* A message without fields gets a boolean ``_present`` marker, since Parquet has no empty structs.
* A proto3 scalar is written with its value, ``0`` and ``false`` included. ``null`` means the
  value is absent: an unset message, an unset ``optional`` field or a ``oneof`` member not set.
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any, cast

import pyarrow as pa
from google.protobuf.descriptor import Descriptor, FieldDescriptor

from msed.v1.tick_pb2 import TickData

if TYPE_CHECKING:
    from google.protobuf.message import Message

EPISODE_ID_COLUMN = "episode_id"
PRESENT_FIELD = "_present"

_F = FieldDescriptor
_SCALARS: dict[int, pa.DataType] = {
    _F.TYPE_DOUBLE: pa.float64(),
    _F.TYPE_FLOAT: pa.float32(),
    _F.TYPE_INT64: pa.int64(),
    _F.TYPE_SINT64: pa.int64(),
    _F.TYPE_SFIXED64: pa.int64(),
    _F.TYPE_UINT64: pa.uint64(),
    _F.TYPE_FIXED64: pa.uint64(),
    _F.TYPE_INT32: pa.int32(),
    _F.TYPE_SINT32: pa.int32(),
    _F.TYPE_SFIXED32: pa.int32(),
    _F.TYPE_UINT32: pa.uint32(),
    _F.TYPE_FIXED32: pa.uint32(),
    _F.TYPE_BOOL: pa.bool_(),
    _F.TYPE_STRING: pa.string(),
    _F.TYPE_BYTES: pa.binary(),
    _F.TYPE_ENUM: pa.int32(),
}


def _descriptor(msg: Message) -> Descriptor:
    # protobuf's C extension and pure-Python descriptors share one interface; the stubs list both.
    return cast("Descriptor", msg.DESCRIPTOR)


def _message(field: FieldDescriptor) -> Descriptor:
    descriptor = field.message_type
    if descriptor is None:
        raise TypeError(f"{field.full_name} is not a message field")
    return descriptor


def _is_map(field: FieldDescriptor) -> bool:
    return field.type == _F.TYPE_MESSAGE and _message(field).GetOptions().map_entry


def _oneofs(descriptor: Descriptor) -> list[str]:
    # Synthetic oneofs (proto3 ``optional``) start with "_" and are not real choices.
    return [o.name for o in descriptor.oneofs if not o.name.startswith("_")]


def _struct_fields(descriptor: Descriptor) -> list[pa.Field]:
    fields = [pa.field(f.name, _field_type(f)) for f in descriptor.fields]
    fields += [pa.field(name, pa.string()) for name in _oneofs(descriptor)]
    if not descriptor.fields:
        fields.append(pa.field(PRESENT_FIELD, pa.bool_()))
    return fields


def _value_type(field: FieldDescriptor) -> pa.DataType:
    if field.type == _F.TYPE_MESSAGE:
        return pa.struct(_struct_fields(_message(field)))
    return _SCALARS[field.type]


def _field_type(field: FieldDescriptor) -> pa.DataType:
    if _is_map(field):
        entry = _message(field)
        key, value = entry.fields_by_name["key"], entry.fields_by_name["value"]
        return pa.list_(
            pa.struct([pa.field("key", _value_type(key)), pa.field("value", _value_type(value))])
        )
    if field.is_repeated:
        return pa.list_(_value_type(field))
    return _value_type(field)


def _columns(descriptor: Descriptor, prefix: str) -> list[pa.Field]:
    cols: list[pa.Field] = []
    for f in descriptor.fields:
        name = prefix + f.name
        if f.type == _F.TYPE_MESSAGE and not f.is_repeated:
            cols += _columns(_message(f), name + ".")
        else:
            cols.append(pa.field(name, _field_type(f)))
    cols += [pa.field(prefix + name, pa.string()) for name in _oneofs(descriptor)]
    if not descriptor.fields:
        cols.append(pa.field(prefix + PRESENT_FIELD, pa.bool_()))
    return cols


def tick_schema() -> pa.Schema:
    """Return the Arrow schema of one tick row: the episode id, then every tick column."""
    fields = [pa.field(EPISODE_ID_COLUMN, pa.string()), *_columns(_descriptor(TickData()), "")]
    return pa.schema(fields, metadata={b"msed.schema": b"msed.v1.TickData"})


def _scalar(msg: Message, field: FieldDescriptor) -> Any:  # noqa: ANN401 - field values get their type from the descriptor
    if field.has_presence and not msg.HasField(field.name):
        return None
    return getattr(msg, field.name)


def _value(field: FieldDescriptor, value: Any) -> Any:  # noqa: ANN401 - field values get their type from the descriptor
    return to_dict(value) if field.type == _F.TYPE_MESSAGE else value


def _repeated(field: FieldDescriptor, value: Any) -> list[Any]:  # noqa: ANN401 - field values get their type from the descriptor
    if _is_map(field):
        val_field = _message(field).fields_by_name["value"]
        return [{"key": k, "value": _value(val_field, value[k])} for k in value]
    return [_value(field, v) for v in value]


def to_dict(msg: Message) -> dict[str, Any]:
    """Convert a message to a nested dict, in the struct layout ``tick_schema`` uses for it."""
    desc = _descriptor(msg)
    out: dict[str, Any] = {}
    for f in desc.fields:
        value = getattr(msg, f.name)
        if f.is_repeated:
            out[f.name] = _repeated(f, value)
        elif f.type == _F.TYPE_MESSAGE:
            out[f.name] = to_dict(value) if msg.HasField(f.name) else None
        else:
            out[f.name] = _scalar(msg, f)
    for name in _oneofs(desc):
        out[name] = msg.WhichOneof(name)
    if not desc.fields:
        out[PRESENT_FIELD] = True
    return out


def _flatten(msg: Message, prefix: str, out: dict[str, Any]) -> None:
    desc = _descriptor(msg)
    for f in desc.fields:
        name = prefix + f.name
        value = getattr(msg, f.name)
        if f.is_repeated:
            out[name] = _repeated(f, value)
        elif f.type == _F.TYPE_MESSAGE:
            if msg.HasField(f.name):
                _flatten(value, name + ".", out)
        else:
            out[name] = _scalar(msg, f)
    for oneof in _oneofs(desc):
        out[prefix + oneof] = msg.WhichOneof(oneof)
    if not desc.fields:
        out[prefix + PRESENT_FIELD] = True


def tick_row(tick: TickData, episode_id: str) -> dict[str, Any]:
    """Flatten one tick into a row of ``tick_schema``; a column missing from the row is null."""
    row: dict[str, Any] = {EPISODE_ID_COLUMN: episode_id}
    _flatten(tick, "", row)
    return row
