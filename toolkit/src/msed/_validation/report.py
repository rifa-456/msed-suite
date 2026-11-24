"""What validating one episode found."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Literal, TypeAlias

if TYPE_CHECKING:
    from pathlib import Path

Level: TypeAlias = Literal["error", "warning"]


@dataclass(slots=True)
class Report:
    """The result of validating one episode; it accumulates while the episode is read.

    Attributes:
        episode_id: The episode id, or the folder name when the manifest cannot be read.
        path: The episode folder.
        ticks: Ticks read.
        first_tick: Index of the first tick read; ``None`` before any tick.
        last_tick: Index of the last tick read; ``None`` before any tick.
        issues: Every finding in the order found, with its level.
    """

    episode_id: str
    path: Path
    ticks: int = 0
    first_tick: int | None = None
    last_tick: int | None = None
    issues: list[tuple[Level, str]] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        """True if nothing makes the data untrustworthy; warnings are allowed."""
        return not self.errors

    @property
    def errors(self) -> list[str]:
        """Findings that make the data untrustworthy."""
        return [message for level, message in self.issues if level == "error"]

    @property
    def warnings(self) -> list[str]:
        """Findings that leave the data valid but degraded."""
        return [message for level, message in self.issues if level == "warning"]

    def error(self, message: str) -> None:
        """Record a finding that makes the data untrustworthy."""
        self.issues.append(("error", message))

    def warn(self, message: str) -> None:
        """Record a finding that leaves the data valid but degraded."""
        self.issues.append(("warning", message))

    def count_tick(self, tick: int) -> None:
        """Record one tick read, in order."""
        self.ticks += 1
        if self.first_tick is None:
            self.first_tick = tick
        self.last_tick = tick
