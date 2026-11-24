import shutil
import subprocess
import sys

import pyarrow.parquet as pq
from click.testing import CliRunner

from msed import Vocabulary, convert
from msed.cli import main


def test_convert_writes_huggingface_layout(episode_factory, tmp_path):
    src = episode_factory()
    out = tmp_path / "dataset"
    result = convert(src.parent, out)

    assert result.converted == ["ep1"]
    ticks = pq.read_table(out / "ticks" / "ep1.parquet")
    assert ticks.num_rows == 10
    assert ticks.column("tick").to_pylist() == list(range(1, 11))

    events = ticks.column("events").to_pylist()
    assert events[2][0]["payload"] == "item_picked_up"
    assert events[2][0]["item_picked_up"] == {"item_id": 7, "count": 2}

    index = pq.read_table(out / "episodes.parquet").to_pylist()
    assert index[0]["episode_id"] == "ep1"
    assert (index[0]["num_ticks"], index[0]["first_tick"], index[0]["last_tick"]) == (10, 1, 10)
    assert index[0]["vocabulary_file"] == "episodes/ep1/vocabulary.json"
    assert (out / index[0]["vocabulary_file"]).is_file()
    assert (out / "README.md").read_text(encoding="utf-8").startswith("---")


def test_column_projection_selects_one_modality(episode_factory, tmp_path):
    convert(episode_factory().parent, tmp_path / "out")
    table = pq.read_table(tmp_path / "out" / "ticks", columns=["tick", "player.health"])
    assert table.column_names == ["tick", "player.health"]


def test_invalid_episodes_are_skipped(episode_factory, tmp_path):
    good = episode_factory(episode_id="good")
    bad = episode_factory(episode_id="bad")
    (bad / "shard_0000.bin").write_bytes(b"garbage")
    result = convert(good.parent, tmp_path / "out")
    assert result.converted == ["good"]
    assert [r.episode_id for r in result.skipped] == ["bad"]


def test_converted_episodes_are_kept_unless_overwrite(episode_factory, tmp_path):
    src = episode_factory()
    out = tmp_path / "out"
    convert(src.parent, out)
    again = convert(src.parent, out)
    assert (again.converted, again.unchanged) == ([], ["ep1"])
    forced = convert(src.parent, out, overwrite=True)
    assert forced.converted == ["ep1"]
    assert pq.read_table(out / "episodes.parquet").num_rows == 1


def test_interrupted_run_resumes(episode_factory, tmp_path):
    first = episode_factory(episode_id="a")
    episode_factory(episode_id="b")
    out = tmp_path / "out"
    convert(first, out)
    # A run stopped while writing "b" leaves a partial tick file and no index row for it.
    (out / "ticks" / "b.parquet.partial").write_bytes(b"partial")
    result = convert(first.parent, out)
    assert (result.converted, result.unchanged) == (["b"], ["a"])
    assert pq.read_table(out / "episodes.parquet").column("episode_id").to_pylist() == ["a", "b"]


def test_vocabulary_reads_mod_format(episode_factory):
    vocab = Vocabulary.load(episode_factory() / "vocabulary.json")
    assert vocab.name("items", 7) == "minecraft:wheat_seeds"
    assert vocab["blocks"][1] == "minecraft:stone"
    assert vocab.name("items", 999) is None
    assert vocab.field_index == {"events.item_picked_up.item_id": "items"}


def test_cli_exit_codes(episode_factory, tmp_path):
    good = episode_factory(episode_id="good")
    runner = CliRunner()
    ok = runner.invoke(main, ["validate", str(good), "--no-frame-count"])
    assert ok.exit_code == 0, ok.output
    assert "1/1 episode(s) valid" in ok.output

    (good / "shard_0000.bin").write_bytes(b"x")
    failed = runner.invoke(main, ["validate", str(good), "--no-frame-count"])
    assert failed.exit_code == 1
    assert "FAILED" in failed.output

    converted = runner.invoke(
        main, ["convert", str(good.parent), str(tmp_path / "o"), "--no-videos"]
    )
    assert converted.exit_code == 1
    assert "skipped 1" in converted.output


def test_cli_reports_episodes_already_converted(episode_factory, tmp_path):
    source = episode_factory()
    runner = CliRunner()
    args = ["convert", str(source.parent), str(tmp_path / "o"), "--no-videos"]
    assert runner.invoke(main, args).exit_code == 0
    again = runner.invoke(main, args)
    assert again.exit_code == 0
    assert "converted 0 episode(s)" in again.output
    assert "1 already there" in again.output


def test_python_dash_m_runs_the_cli():
    result = subprocess.run(
        [sys.executable, "-m", "msed", "--version"], capture_output=True, text=True, check=True
    )
    assert result.stdout.startswith("msed, version ")


def test_allow_errors_keeps_the_readable_ticks_and_goes_on(episode_factory, tmp_path):
    damaged = episode_factory(episode_id="damaged")
    episode_factory(episode_id="fine")
    shard = damaged / "shard_0002.bin"
    shard.write_bytes(shard.read_bytes()[:-3])
    out = tmp_path / "out"
    result = convert(damaged.parent, out, copy_videos=False, allow_errors=True)
    assert result.converted == ["damaged", "fine"]
    assert [r.episode_id for r in result.with_errors] == ["damaged"]
    assert pq.read_table(out / "ticks" / "damaged.parquet").num_rows == 9
    assert not list((out / "ticks").glob("*.partial"))


def test_a_second_folder_with_the_same_episode_id_is_skipped(episode_factory, tmp_path):
    first = episode_factory(episode_id="same")
    shutil.copytree(first, first.parent / "copy")
    result = convert(first.parent, tmp_path / "out", copy_videos=False)
    assert result.converted == ["same"]
    assert "also the id of another folder" in result.skipped[0].errors[0]
