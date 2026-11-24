# Converting and publishing

The `msed` toolkit checks episode folders and turns them into a Parquet dataset. It runs after
the game, on any computer with Python {{ python_version }} or newer.

## Installing the toolkit

```sh
pip install msed
```

or, to keep it apart from other Python projects, `uv tool install msed` or `pipx install msed`.
Counting video frames needs `ffprobe` or `ffmpeg` on `PATH`. If the `msed` command is not
found after installing, run `python -m msed` instead.

## Validating episodes

```sh
msed validate .minecraft/dataset
```

The argument is one episode folder or a folder of episodes. For each episode, `msed validate`
reports **errors**, which mean the data cannot be trusted, and **warnings**, which mean it is
valid but degraded.

Errors:

- There is no `metadata.json`: the recording did not finish, because the game closed or crashed
  before it stopped.
- The manifest names a file outside the episode folder.
- A shard is missing, or its size or SHA-256 differs from the manifest.
- A record cannot be read.
- Tick numbers go backwards.
- A tick points past the end of a video.

Warnings:

- Ticks were dropped because the disk writer fell behind.
- A tick repeats the previous video frame.
- The last tick has no stop event: the game closed during a recording.

```text
OK  20261123_114236_399  (300 ticks, ticks 1..300)
FAILED  20261123_120511_042  (1200 ticks, ticks 1..1200)
  error: shard_0001.bin: SHA-256 mismatch

1/2 episode(s) valid
```

The command exits with status 1 when any episode has errors. `--no-frame-count` skips counting
the frames of each video, which reads every video once.

## Converting

```sh
msed convert .minecraft/dataset my-dataset
```

Each episode is validated first; an episode with errors is reported and left out. With
`--allow-errors` it is converted anyway, with every tick that can be read, and its errors are
still printed. Two folders with the same episode id are never both converted. The output is a folder that uploads to Hugging Face as it is:

```text
my-dataset/
├── README.md                         dataset card
├── episodes.parquet                  one row per episode
├── ticks/
│   └── 20261123_114236_399.parquet   one row per tick
└── episodes/
    └── 20261123_114236_399/
        ├── metadata.json
        ├── vocabulary.json
        ├── world.mp4
        └── gui.mp4
```

An episode is written completely, its index row last, before the next one starts. Running the
command again on the same output converts only the episodes not there yet, so a run that stops
midway continues where it left off, and new recordings can be added to an existing dataset.

[Command line](cli.md) lists every option of both commands.

The dataset card in `README.md` is written once and never overwritten, so you can edit it.

## Publishing on Hugging Face

```sh
hf auth login
hf upload your-name/my-dataset my-dataset --repo-type dataset
```

`hf` comes with `pip install huggingface_hub`. The dataset card declares two configurations,
`ticks` (the default) and `episodes`, so the Hugging Face dataset viewer shows both tables.
