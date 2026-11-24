import pyarrow as pa
from google.protobuf.descriptor import Descriptor, FieldDescriptor

from msed import tick_row, tick_schema
from msed._dataset.columns import EPISODE_ID_COLUMN, _descriptor, _message
from msed.v1.tick_pb2 import TickData
from tests.conftest import make_tick


def _leaf_paths(desc: Descriptor, prefix: str = "") -> set[str]:
    paths = set()
    for f in desc.fields:
        if f.type == FieldDescriptor.TYPE_MESSAGE and not f.is_repeated:
            paths |= _leaf_paths(_message(f), prefix + f.name + ".")
        else:
            paths.add(prefix + f.name)
    return paths


def test_every_proto_field_has_a_column():
    """Every field of the protobuf schema has a column, so none is left out of a dataset."""
    columns = set(tick_schema().names)
    assert _leaf_paths(_descriptor(TickData())) <= columns
    assert EPISODE_ID_COLUMN in columns


def test_row_matches_schema_and_keeps_proto3_defaults():
    tick = make_tick(3)  # on_ground is False, a proto3 default
    row = tick_row(tick, "ep1")
    table = pa.Table.from_pylist([row], schema=tick_schema())
    out = table.to_pylist()[0]
    assert out["episode_id"] == "ep1"
    assert out["tick"] == 3
    assert out["player.on_ground"] is False
    assert out["player.health"] == 20.0
    assert out["vision.world_frame.frame_index"] == 2


def test_unset_submessage_is_null():
    table = pa.Table.from_pylist([tick_row(TickData(tick=1), "e")], schema=tick_schema())
    out = table.to_pylist()[0]
    assert out["player.health"] is None
    assert out["events"] == []


def test_oneof_case_and_empty_messages():
    row = tick_row(make_tick(10, stopped=True), "e")
    out = pa.Table.from_pylist([row], schema=tick_schema()).to_pylist()[0]
    stop = out["events"][-1]
    assert stop["payload"] == "stopped"
    assert stop["stopped"] == {"_present": True}
    assert stop["item_picked_up"] is None
