# MkDocs loads this file by path; it is not part of a package.
"""MkDocs hook: write the schema reference from the ``.proto`` files and their comments.

Pages with a ``<!-- schema:NAME -->`` marker get generated content in its place:

* ``schema:columns``: every column of ``ticks/*.parquet``, flattened the way the toolkit flattens
  a tick, with its type and the field's comment.
* ``schema:messages``: every message and enum of the schema, with a table of its fields.

protoc runs on each build with ``--include_source_info``, so the comments in ``proto/`` are the
only place the reference is written.
"""

from __future__ import annotations

import re
from collections import Counter
from typing import TYPE_CHECKING

from proto_schema import ENUM_VALUES, FDP, MESSAGE_FIELDS, PACKAGE, Message, Schema, anchor, load

if TYPE_CHECKING:
    from google.protobuf import descriptor_pb2
    from mkdocs.config.defaults import MkDocsConfig
    from mkdocs.structure.files import Files
    from mkdocs.structure.pages import Page

MARKER = re.compile(r"<!-- schema:(columns|messages) -->")
SCALARS: dict[int, str] = {
    FDP.TYPE_DOUBLE: "double",
    FDP.TYPE_FLOAT: "float",
    FDP.TYPE_INT64: "int64",
    FDP.TYPE_UINT64: "uint64",
    FDP.TYPE_INT32: "int32",
    FDP.TYPE_BOOL: "bool",
    FDP.TYPE_STRING: "string",
    FDP.TYPE_BYTES: "bytes",
    FDP.TYPE_UINT32: "uint32",
    FDP.TYPE_SINT32: "sint32",
    FDP.TYPE_SINT64: "sint64",
}
# Reference sections in reading order: the tick, its modalities, then the episode's other files.
SECTIONS = {
    "msed/v1/tick.proto": "Tick",
    "msed/v1/action.proto": "Action",
    "msed/v1/vision.proto": "Vision",
    "msed/v1/hearing.proto": "Hearing",
    "msed/v1/player.proto": "Player",
    "msed/v1/inventory.proto": "Inventory",
    "msed/v1/gui.proto": "GUI",
    "msed/v1/events.proto": "Events",
    "msed/v1/common.proto": "Shared types",
    "msed/v1/metadata.proto": "Episode manifest",
    "msed/v1/vocabulary.proto": "Vocabulary",
    "msed/v1/config.proto": "Settings",
    "msed/v1/options.proto": "Schema options",
}
# Column groups of ticks/*.parquet, by the first part of the column name, in reading order.
COLUMN_GROUPS = {
    "": "Tick",
    "action": "Action",
    "vision": "Vision",
    "hearing": "Hearing",
    "player": "Player",
    "inventory": "Inventory",
    "gui": "GUI",
    "events": "Events",
}
TICK = f"{PACKAGE}TickData"
VOCAB = f"{PACKAGE}Vocab"


def _field_type(schema: Schema, field: descriptor_pb2.FieldDescriptorProto, page: str = "") -> str:
    kind: str
    if field.type in (FDP.TYPE_MESSAGE, FDP.TYPE_ENUM):
        target = schema.messages.get(field.type_name)
        if target is not None and target.proto.options.map_entry:
            key, value = target.proto.field
            return f"map<{_field_type(schema, key, page)}, {_field_type(schema, value, page)}>"
        kind = field.type_name.removeprefix(PACKAGE).removeprefix(".google.protobuf.")
        if field.type_name.startswith(PACKAGE):
            kind = f"[{kind}]({page}#{anchor(kind)})"
    else:
        kind = SCALARS[field.type]
    if field.label == FDP.LABEL_REPEATED and not kind.startswith("map<"):
        return f"list of {kind}"
    if field.proto3_optional:
        return f"optional {kind}"
    return kind


def _description(schema: Schema, message: Message, index: int) -> str:
    field = message.proto.field[index]
    text = schema.comment(message.file, (*message.path, MESSAGE_FIELDS, index)).replace("|", "\\|")
    number = schema.int_option(field, "vocab")
    if number is None:
        return text
    category = next(v.name for v in schema.enums[VOCAB].proto.value if v.number == number)
    return f"{text} Id from the **{category.removeprefix('VOCAB_').lower()}** vocabulary."


def _columns(schema: Schema) -> str:
    groups: dict[str, list[str]] = {key: [] for key in COLUMN_GROUPS}
    groups[""].append("| `episode_id` | string | The episode the tick belongs to. |")

    def add(name: str, kind: str, text: str) -> None:
        group = name.split(".", 1)[0] if "." in name or name in groups else ""
        groups[group].append(f"| `{name}` | {kind} | {text} |")

    # The comments of a type used by several fields, such as Vec3, cannot say which field they are
    # under, so its columns start with the parent field's comment: "Feet position in world
    # coordinates, in blocks." then "East is positive.". A type used once has comments written for
    # its one place, and needs no prefix.
    uses = Counter(
        field.type_name for message in schema.messages.values() for field in message.proto.field
    )

    def walk(message: Message, prefix: str, context: str) -> None:
        for i, field in enumerate(message.proto.field):
            target = schema.messages.get(field.type_name)
            flatten = field.type == FDP.TYPE_MESSAGE and field.label != FDP.LABEL_REPEATED
            if flatten and target is not None and not target.proto.options.map_entry:
                parent = schema.comment(message.file, (*message.path, MESSAGE_FIELDS, i))
                shared = uses[field.type_name] > 1
                walk(target, f"{prefix}{field.name}.", parent if shared else "")
                continue
            kind = _field_type(schema, field, "schema.md")
            text = " ".join(filter(None, (context, _description(schema, message, i))))
            add(prefix + field.name, kind, text)
        for oneof in message.proto.oneof_decl:
            if not oneof.name.startswith("_"):
                text = f"Which field of the `{oneof.name}` choice is set."
                add(prefix + oneof.name, "string", text)

    walk(schema.messages[TICK], "", "")
    out: list[str] = []
    for key, title in COLUMN_GROUPS.items():
        if groups[key]:
            out += [f"## {title}", "", "| Column | Type | Description |", "|---|---|---|"]
            out += [*groups[key], ""]
    return "\n".join(out)


def _messages(schema: Schema) -> str:
    out: list[str] = []
    files = list(SECTIONS)
    ordered = sorted(schema.order, key=lambda full: files.index(_file_of(schema, full)))
    section = ""
    for full in ordered:
        file = _file_of(schema, full)
        if file != section:
            section = file
            out.append(f"## {SECTIONS[file]}\n")
        if full in schema.enums:
            enum = schema.enums[full]
            out.append(f"### {enum.name} {{#{anchor(enum.name)}}}\n")
            if text := schema.comment(enum.file, enum.path):
                out.append(text + "\n")
            out.append("| Value | Number | Description |\n|---|---|---|")
            for i, value in enumerate(enum.proto.value):
                text = schema.comment(enum.file, (*enum.path, ENUM_VALUES, i)).replace("|", "\\|")
                out.append(f"| `{value.name}` | {value.number} | {text} |")
            out.append("")
            continue
        message = schema.messages[full]
        out.append(f"### {message.name} {{#{anchor(message.name)}}}\n")
        if text := schema.comment(message.file, message.path):
            out.append(text + "\n")
        if not message.proto.field:
            out.append("No fields: the message marks that the event happened.\n")
            continue
        out.append("| Field | Type | Description |\n|---|---|---|")
        for i, field in enumerate(message.proto.field):
            kind = _field_type(schema, field)
            out.append(f"| `{field.name}` | {kind} | {_description(schema, message, i)} |")
        out.append("")
    return "\n".join(out)


def _file_of(schema: Schema, full: str) -> str:
    return schema.enums[full].file if full in schema.enums else schema.messages[full].file


RENDER = {"columns": _columns, "messages": _messages}


def on_page_markdown(markdown: str, page: Page, config: MkDocsConfig, files: Files) -> str:  # noqa: ARG001
    """Replace each schema marker of a page with the generated reference."""
    if not MARKER.search(markdown):
        return markdown
    return MARKER.sub(lambda m: RENDER[m.group(1)](load()), markdown)
