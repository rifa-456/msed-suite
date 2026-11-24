"""Hatch build hook: generate the Python protobuf modules from the shared schema.

The ``.proto`` files at the repository root are the schema the mod records with. They are
compiled into ``src/msed/v1/`` on every build (wheel, editable install and sdist), so the
generated code always matches them and is never committed.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import grpc_tools
from grpc_tools import protoc
from hatchling.builders.config import BuilderConfig
from hatchling.builders.hooks.plugin.interface import BuildHookInterface


class ProtoBuildHook(BuildHookInterface[BuilderConfig]):
    """The build step that compiles the schema into ``src/msed/v1``."""

    PLUGIN_NAME = "custom"

    def initialize(self, _version: str, _build_data: dict[str, Any]) -> None:
        """Generate the protobuf modules and their type stubs."""
        root = Path(self.root)
        # Repository checkout: ../proto. Unpacked sdist: ./proto.
        proto_root = next(
            (p for p in (root / "proto", root.parent / "proto") if (p / "msed" / "v1").is_dir()),
            None,
        )
        if proto_root is None:
            raise RuntimeError("MSED schema not found: expected proto/msed/v1 next to the toolkit")

        out_dir = root / "src"
        well_known = Path(grpc_tools.__file__).parent / "_proto"
        # protoc expects import-style paths (forward slashes) relative to an -I root.
        sources = sorted(
            p.relative_to(proto_root).as_posix()
            for p in (proto_root / "msed" / "v1").glob("*.proto")
        )

        args = [
            "protoc",
            f"-I{proto_root}",
            f"-I{well_known}",
            f"--python_out={out_dir}",
            f"--pyi_out={out_dir}",
            *sources,
        ]
        if protoc.main(args) != 0:
            raise RuntimeError("protoc failed to compile the MSED schema")

        init = out_dir / "msed" / "v1" / "__init__.py"
        init.write_text(
            '"""Generated protobuf modules for the MSED v1 schema."""\n', encoding="utf-8"
        )
        self.app.display_info(f"Generated {len(sources)} protobuf modules into src/msed/v1")
