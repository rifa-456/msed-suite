---
license: mit
tags:
- minecraft
- msed
- world-models
- reinforcement-learning
configs:
- config_name: ticks
  data_files: ticks/*.parquet
  default: true
- config_name: episodes
  data_files: episodes.parquet
---

# MSED Minecraft recordings

Tick-aligned trajectories recorded with [MSED](https://github.com/rifa-456/msed-suite).

* `ticks/*.parquet`: one row per game tick (20 Hz) with structured state, actions and events.
* `episodes.parquet`: one row per episode with metadata and file paths.
* `episodes/<episode_id>/`: `vocabulary.json` (integer ID to name), raw `metadata.json`,
  and the `world.mp4` / `gui.mp4` videos. Frame `vision.world_frame.frame_index` of a tick is
  its frame in `world.mp4`.

Column reference: https://msed-suite.readthedocs.io/en/latest/columns/
