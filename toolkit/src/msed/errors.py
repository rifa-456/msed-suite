"""Errors the toolkit raises."""


class EpisodeError(Exception):
    """An episode cannot be read.

    A file is missing or truncated, or the episode was written by another MSED major version.
    """
