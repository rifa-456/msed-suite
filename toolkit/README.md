# msed

Validate and convert Minecraft recordings made with the [MSED](https://github.com/rifa-456/msed-suite)
mod. The mod writes each recording as an episode folder of protobuf shards, a manifest, a
vocabulary and two videos; `msed` checks those folders and turns them into a Parquet dataset that
uploads to Hugging Face as it is.

```sh
pip install msed
```

Python 3.10 or newer. Counting video frames during validation uses `ffprobe` or `ffmpeg` when one
of them is installed.

## Commands

```sh
msed validate DATASET_DIR
msed convert DATASET_DIR OUTPUT_DIR
```

`DATASET_DIR` is one episode folder or a folder of episodes, such as `.minecraft/dataset`.

- `msed validate` checks each episode's shards (size, SHA-256, readable records), its tick
  sequence and its video frame counts. It exits with status 1 when an episode has errors;
  `--no-frame-count` skips reading the videos.
- `msed convert` validates each episode and writes it to `OUTPUT_DIR` as
  `ticks/<episode_id>.parquet`, a row in `episodes.parquet`, and `episodes/<episode_id>/` with
  the manifest, vocabulary and videos. Episodes already in `OUTPUT_DIR` are kept, so a run that
  stops midway continues where it left off. `--no-videos` leaves the videos out,
  `--overwrite` converts existing episodes again, and `--allow-errors` converts episodes that
  failed validation.

`python -m msed` runs the same commands when the `msed` script is not on `PATH`.

## Python API

```python
import msed

report = msed.validate_episode("dataset/20261123_114236_399")
print(report.ok, report.errors, report.warnings)

episode = msed.Episode.open("dataset/20261123_114236_399")
for tick in episode.iter_ticks():
    print(tick.tick, tick.player.position.x)
```

`msed.v1` holds the protobuf messages of the schema.

Documentation: https://msed-suite.readthedocs.io
