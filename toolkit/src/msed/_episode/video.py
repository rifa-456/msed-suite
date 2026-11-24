"""Frame counts of the episode videos, read with the FFmpeg installed on the system."""

from __future__ import annotations

import re
import shutil
import subprocess
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from pathlib import Path

_FRAME = re.compile(r"frame=\s*(\d+)")


def video_frame_count(path: Path) -> int | None:
    """Count the frames of a video with ffprobe, or with ffmpeg when only ffmpeg is installed.

    Args:
        path: The video file.

    Returns:
        The number of frames; ``None`` when neither tool is on ``PATH`` or the file is unreadable.
    """
    if ffprobe := shutil.which("ffprobe"):
        probe = subprocess.run(
            [
                ffprobe,
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-count_packets",
                "-show_entries",
                "stream=nb_read_packets",
                "-of",
                "csv=p=0",
                str(path),
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if probe.stdout.strip().isdigit():
            return int(probe.stdout.strip())
    if ffmpeg := shutil.which("ffmpeg"):
        # Remuxing to null reads every packet without decoding; the last progress line counts them.
        remux = subprocess.run(
            [
                ffmpeg,
                "-hide_banner",
                "-nostdin",
                "-i",
                str(path),
                "-map",
                "0:v:0",
                "-c",
                "copy",
                "-f",
                "null",
                "-",
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        counts = _FRAME.findall(remux.stderr)
        if counts:
            return int(counts[-1])
    return None
