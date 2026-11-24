# MkDocs loads this file by path; it is not part of a package.
"""MkDocs hook: write the settings reference in place of ``<!-- schema:settings -->``.

Each settings group of ``config.proto`` becomes a section with one row per setting: its name
on the config screen (from the mod's English lang file), its key in ``msed.json``, its default and
range (from the ``default_*``, ``min_*`` and ``max_*`` field options), and the field's comment.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import TYPE_CHECKING

from proto_schema import FDP, MESSAGE_FIELDS, PACKAGE, Message, Schema, load

if TYPE_CHECKING:
    from google.protobuf import descriptor_pb2
    from mkdocs.config.defaults import MkDocsConfig
    from mkdocs.structure.files import Files
    from mkdocs.structure.pages import Page

MARKER = "<!-- schema:settings -->"
LANG = Path(__file__).resolve().parents[2] / "mod/src/main/resources/assets/msed/lang/en_us.json"
# Lang keys of the config screen: a group's label, and a setting's label by its msed.json path.
CATEGORY_KEY = "text.autoconfig.msed.category."
OPTION_KEY = "text.autoconfig.msed.option."
CONFIG = f"{PACKAGE}MsedConfig"


def _default(schema: Schema, field: descriptor_pb2.FieldDescriptorProto) -> str:
    if field.type == FDP.TYPE_BOOL:
        return "on" if schema.int_option(field, "default_bool") else "off"
    if field.type == FDP.TYPE_STRING:
        text = schema.string_option(field, "default_string")
        return f'`"{text}"`' if text else "empty"
    if field.type == FDP.TYPE_FLOAT:
        return f"{schema.float_option(field, 'default_float') or 0.0:g}"
    return str(schema.int_option(field, "default_int") or 0)


def _range(schema: Schema, field: descriptor_pb2.FieldDescriptorProto) -> str:
    if field.type == FDP.TYPE_FLOAT:
        low = schema.float_option(field, "min_float")
        high = schema.float_option(field, "max_float")
    else:
        low = schema.int_option(field, "min_int")
        high = schema.int_option(field, "max_int")
    if low is not None and high is not None:
        return f"{low:g} to {high:g}"
    if low is not None:
        return f"at least {low:g}"
    if high is not None:
        return f"at most {high:g}"
    return ""


def _rows(
    schema: Schema, labels: dict[str, str], message: Message, path: str, prefix: str
) -> list[tuple[str, str, str, str]]:
    rows: list[tuple[str, str, str, str]] = []
    for i, field in enumerate(message.proto.field):
        key = f"{path}.{field.json_name}"
        label = prefix + labels[OPTION_KEY + key]
        if field.type == FDP.TYPE_MESSAGE:
            nested = schema.messages[field.type_name]
            rows += _rows(schema, labels, nested, key, f"{label}: ")
            continue
        text = schema.comment(message.file, (*message.path, MESSAGE_FIELDS, i))
        setting = f"{label}<br>`{key}`"
        rows.append((setting, _default(schema, field), _range(schema, field), text))
    return rows


def _settings(schema: Schema) -> str:
    labels: dict[str, str] = json.loads(LANG.read_text(encoding="utf-8"))
    config = schema.messages[CONFIG]
    out: list[str] = []
    for i, group in enumerate(config.proto.field):
        rows = _rows(schema, labels, schema.messages[group.type_name], group.json_name, "")
        ranged = any(row[2] for row in rows)
        out += [f"## {labels[CATEGORY_KEY + group.json_name]}", ""]
        if text := schema.comment(config.file, (*config.path, MESSAGE_FIELDS, i)):
            out += [text, ""]
        if ranged:
            out += ["| Setting | Default | Range | Description |", "|---|---|---|---|"]
            out += [f"| {s} | {d} | {r} | {t} |" for s, d, r, t in rows]
        else:
            out += ["| Setting | Default | Description |", "|---|---|---|"]
            out += [f"| {s} | {d} | {t} |" for s, d, _, t in rows]
        out.append("")
    return "\n".join(out)


def on_page_markdown(markdown: str, page: Page, config: MkDocsConfig, files: Files) -> str:  # noqa: ARG001
    """Replace the settings marker of a page with the generated reference."""
    if MARKER not in markdown:
        return markdown
    return markdown.replace(MARKER, _settings(load()))
