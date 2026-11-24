"""Command line interface: ``msed validate`` and ``msed convert``."""

from __future__ import annotations

from pathlib import Path

import click

from msed import Report, __version__, convert, find_episodes, validate_episode
from msed.errors import EpisodeError

_PATH = click.Path(exists=True, file_okay=False, path_type=Path)


def _print_report(report: Report) -> None:
    status = click.style("OK", fg="green") if report.ok else click.style("FAILED", fg="red")
    span = f"ticks {report.first_tick}..{report.last_tick}" if report.ticks else "no ticks"
    click.echo(f"{status}  {report.episode_id}  ({report.ticks} ticks, {span})")
    for message in report.errors:
        click.echo(click.style("  error: ", fg="red") + message)
    for message in report.warnings:
        click.echo(click.style("  warning: ", fg="yellow") + message)


@click.group(context_settings={"help_option_names": ["-h", "--help"]})
@click.version_option(__version__, prog_name="msed")
def main() -> None:
    """Validate and convert MSED Minecraft recordings."""


@main.command("validate")
@click.argument("source", type=_PATH)
@click.option("--no-frame-count", is_flag=True, help="Skip counting the frames of each video.")
@click.pass_context
def validate_command(ctx: click.Context, source: Path, *, no_frame_count: bool) -> None:
    """Check episodes in SOURCE for corruption and alignment problems.

    SOURCE is one episode folder or a folder of episodes. Exits with status 1 if any episode has
    errors. Counting video frames needs ffprobe or ffmpeg and reads every video once.
    """
    try:
        episodes = find_episodes(source)
    except EpisodeError as e:
        raise click.ClickException(str(e)) from e
    if not episodes:
        raise click.ClickException(f"no episodes found in {source}")
    reports = [validate_episode(episode, check_videos=not no_frame_count) for episode in episodes]
    for report in reports:
        _print_report(report)
    failed = sum(not r.ok for r in reports)
    click.echo(f"\n{len(reports) - failed}/{len(reports)} episode(s) valid")
    ctx.exit(1 if failed else 0)


@main.command("convert")
@click.argument("source", type=_PATH)
@click.argument("output", type=click.Path(file_okay=False, path_type=Path))
@click.option("--no-videos", is_flag=True, help="Leave the videos out of the dataset.")
@click.option("--overwrite", is_flag=True, help="Convert episodes already in OUTPUT again.")
@click.option("--allow-errors", is_flag=True, help="Convert episodes even if validation fails.")
@click.pass_context
def convert_command(
    ctx: click.Context,
    source: Path,
    output: Path,
    *,
    no_videos: bool,
    overwrite: bool,
    allow_errors: bool,
) -> None:
    """Convert episodes in SOURCE into a Parquet dataset in OUTPUT.

    Every episode is validated first; episodes with errors are skipped unless --allow-errors.
    Episodes already in OUTPUT are kept, so an interrupted run continues where it stopped.
    OUTPUT can be uploaded to Hugging Face as-is.
    """
    try:
        result = convert(
            source,
            output,
            copy_videos=not no_videos,
            overwrite=overwrite,
            allow_errors=allow_errors,
        )
    except EpisodeError as e:
        raise click.ClickException(str(e)) from e
    for report in (*result.with_errors, *result.skipped):
        _print_report(report)
    summary = f"\nconverted {len(result.converted)} episode(s) into {output}"
    if result.with_errors:
        summary += f" ({len(result.with_errors)} with errors)"
    if result.unchanged:
        summary += f", {len(result.unchanged)} already there"
    if result.skipped:
        summary += f", skipped {len(result.skipped)} with errors"
    click.echo(summary)
    ctx.exit(1 if result.skipped else 0)
