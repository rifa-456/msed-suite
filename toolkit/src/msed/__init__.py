"""MSED toolkit: validate Minecraft recordings made with the MSED mod and convert them to Parquet.

``msed validate`` and ``msed convert`` are the command line; the same work is available as
`validate_episode` and `convert`. `Episode` reads a raw episode and `Vocabulary` turns its integer
ids into names. The protobuf schema is in ``msed.v1``.
"""

from msed._dataset.columns import tick_row, tick_schema
from msed._dataset.convert import ConvertResult, convert
from msed._episode.folder import Episode, find_episodes
from msed._episode.vocabulary import Vocabulary
from msed._validation.checks import validate_episode
from msed._validation.report import Report
from msed._version import __version__
from msed.errors import EpisodeError

__all__ = [
    "ConvertResult",
    "Episode",
    "EpisodeError",
    "Report",
    "Vocabulary",
    "__version__",
    "convert",
    "find_episodes",
    "tick_row",
    "tick_schema",
    "validate_episode",
]
