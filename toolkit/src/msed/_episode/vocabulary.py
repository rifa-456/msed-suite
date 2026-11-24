"""Names for the integer ids a recording stores.

Each episode ships a ``vocabulary.json`` (an ``EpisodeVocabulary`` message in JSON form)::

    {
      "blocks":   {"entries": {"0": "minecraft:air", "1": "minecraft:stone", ...}},
      "items":    {"entries": {...}},
      ...
      "field_index": {"tick.inventory.hotbar.item_id": "VOCAB_ITEMS", ...}
    }

Registry categories (blocks, items, entities, sounds, effects) come straight from the game
registries and are stable for a fixed game version and mod list. Hash categories (screens,
semantic_tags, events) only contain values observed in that session.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from google.protobuf import json_format

from msed.v1.options_pb2 import Vocab
from msed.v1.vocabulary_pb2 import EpisodeVocabulary

CATEGORIES = (
    "blocks",
    "items",
    "entities",
    "sounds",
    "effects",
    "screens",
    "semantic_tags",
    "events",
)


@dataclass(frozen=True, slots=True)
class Vocabulary:
    """The id-to-name maps of one episode.

    Attributes:
        maps: For each category, the name of every id.
        field_index: The category of each column that holds ids, such as
            ``player.equipped_items.main_hand_item_id`` -> ``items``. The paths match the Parquet
            columns; list items are not marked.
    """

    maps: dict[str, dict[int, str]]
    field_index: dict[str, str]

    @classmethod
    def load(cls, path: str | Path) -> Vocabulary:
        """Read an episode's ``vocabulary.json``.

        Args:
            path: The ``vocabulary.json`` file.

        Returns:
            The vocabulary.
        """
        msg = EpisodeVocabulary()
        json_format.Parse(Path(path).read_text(encoding="utf-8"), msg)
        maps = {name: dict(getattr(msg, name).entries) for name in CATEGORIES}
        field_index = {
            field.removeprefix("tick."): Vocab.Name(vocab).removeprefix("VOCAB_").lower()
            for field, vocab in msg.field_index.items()
        }
        return cls(maps=maps, field_index=field_index)

    def name(self, category: str, ident: int) -> str | None:
        """Return the name of ``ident`` in ``category``, for example ``name("items", 42)``.

        Args:
            category: The vocabulary category, such as ``"items"`` or ``"screens"``.
            ident: The integer id a tick recorded.

        Returns:
            The name; ``None`` when the episode's vocabulary has no such id.
        """
        return self.maps[category].get(ident)

    def __getitem__(self, category: str) -> dict[int, str]:
        """Return every id and name of one category."""
        return self.maps[category]
