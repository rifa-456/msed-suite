# MkDocs loads this file by path; it is not part of a package.
"""MkDocs hook: fill ``{{ name }}`` placeholders with versions from the build files.

``mod/gradle.properties`` gives the mod, Minecraft, Fabric Loader and Fabric API versions, and
``toolkit/pyproject.toml`` the oldest supported Python. ``fabric_api_url`` is the download of
the exact Fabric API jar the mod is built against. A placeholder with no value fails the build.
"""

from __future__ import annotations

import re
import tomllib
from pathlib import Path
from typing import TYPE_CHECKING
from urllib.parse import quote

if TYPE_CHECKING:
    from mkdocs.config.defaults import MkDocsConfig
    from mkdocs.structure.files import Files
    from mkdocs.structure.pages import Page

ROOT = Path(__file__).resolve().parents[2]
PLACEHOLDER = re.compile(r"\{\{ (\w+) \}\}")
GRADLE_KEYS = ("mod_version", "minecraft_version", "loader_version", "fabric_api_version")
FABRIC_API_JAR = "https://github.com/FabricMC/fabric-api/releases/download/{0}/fabric-api-{0}.jar"


def _versions() -> dict[str, str]:
    lines = (ROOT / "mod/gradle.properties").read_text(encoding="utf-8").splitlines()
    gradle = dict(line.split("=", 1) for line in lines if "=" in line and not line.startswith("#"))
    toolkit = tomllib.loads((ROOT / "toolkit/pyproject.toml").read_text(encoding="utf-8"))
    python = toolkit["project"]["requires-python"].removeprefix(">=")
    fabric_api = quote(gradle["fabric_api_version"])
    return {key: gradle[key] for key in GRADLE_KEYS} | {
        "python_version": python,
        "fabric_api_url": FABRIC_API_JAR.format(fabric_api),
    }


def on_page_markdown(markdown: str, page: Page, config: MkDocsConfig, files: Files) -> str:  # noqa: ARG001
    """Replace each version placeholder of a page with its value."""
    versions = _versions()
    return PLACEHOLDER.sub(lambda m: versions[m.group(1)], markdown)
