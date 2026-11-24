# Changelog

All notable changes to the mod, the toolkit and the schema are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/): the toolkit reads episodes of its own major version.

## [1.0.0] - 2026-11-24

The first release, matching the paper *MSED: Engine-Level Multi-Modal Trajectory Recording for
Minecraft* (IEEE CoG 2026).

### Mod

- Records one tick record per client tick: inputs and held bindings, video frame indices, ray
  grids, sounds, player state, inventory, the open screen, and the events of the tick.
- Captures a world-only and a GUI video with exactly one frame per tick, encoded by FFmpeg as
  fragmented MP4.
- Writes each episode as Protobuf shards with a `metadata.json` manifest and a `vocabulary.json`.
- Blocks the inputs that have no gameplay effect, such as chat and F3, so the player acts only
  through recorded inputs.
- A settings screen on F7, with Cloth Config bundled, and preview overlays on F6, F8, F9 and F10.

### Toolkit

- `msed validate` checks shard sizes and checksums, tick order and frame indices.
- `msed convert` writes a Parquet dataset ready for Hugging Face and resumes an interrupted run.
- Python API: `validate_episode`, `convert`, `Episode` and `Vocabulary`.

[1.0.0]: https://github.com/rifa-456/msed-suite/releases/tag/v1.0.0
