<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/banner-dark.svg">
  <img alt="MSED" src="docs/assets/banner-light.svg" width="100%">
</picture>

[![CI](https://github.com/rifa-456/msed-suite/actions/workflows/ci.yml/badge.svg)](https://github.com/rifa-456/msed-suite/actions/workflows/ci.yml)
[![PyPI](https://img.shields.io/pypi/v/msed)](https://pypi.org/project/msed/)
[![Docs](https://readthedocs.org/projects/msed-suite/badge/?version=latest)](https://msed-suite.readthedocs.io)
[![DOI](https://img.shields.io/badge/DOI-10.1109%2FCoG67430.2026.11778848-blue)](https://doi.org/10.1109/CoG67430.2026.11778848)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

MSED records Minecraft gameplay from inside the game engine. Each of the game's 20 ticks per
second becomes one record holding the player's inputs, the game state the player could act on,
and the events of that tick, next to two videos with exactly one frame per tick. The recordings
convert to a Parquet dataset that uploads to Hugging Face as it is.

It is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft 1.21.11, plus `msed`, a
Python toolkit that validates and converts the recordings.

## Contents of a tick

| Modality | What is recorded |
|---|---|
| Action | Every key, mouse button, scroll and typed character, with camera movement and the gameplay bindings held at the end of the tick |
| Vision | A world-only and a GUI-composited video frame, the camera pose of that frame, and two ray grids with the block id and distance per ray |
| Player | Position, velocity, rotation, health, hunger, air, experience, status effects, combat and mining state, and the entities within 16 blocks |
| Inventory | Hotbar, main inventory, armor and offhand, as item ids with count and damage |
| GUI | The open screen, every slot with its item, the cursor item and the hovered slot |
| Hearing | Every sound the game plays: its id, volume and position |
| Events | Damage, deaths, pickups, drops, block breaks, eating, sleeping, respawns, advancements, and screens opening and closing |

Ids for blocks, items, entities, sounds and effects come from the game's registries and stay the
same across sessions with the same game version and mod list. Each episode's `vocabulary.json`
maps every id to its name.

## Recording

1. Install Fabric for Minecraft 1.21.11 with the [Fabric installer](https://fabricmc.net/use/installer/).
   Then put two files in your `mods` folder: the MSED jar from the
   [latest release](https://github.com/rifa-456/msed-suite/releases/latest), and
   [`fabric-api-0.141.1+1.21.11.jar`](https://github.com/FabricMC/fabric-api/releases/download/0.141.1%2B1.21.11/fabric-api-0.141.1%2B1.21.11.jar),
   the library it needs. [Recording](https://msed-suite.readthedocs.io/recording/) has the steps
   for each system.
2. Install FFmpeg for the videos: `winget install ffmpeg` on Windows, `brew install ffmpeg` on
   macOS, or your distribution's package on Linux. Without it, MSED records everything except
   the videos and says so in chat.
3. Open a singleplayer world and press **F12** twice to start recording; press it twice again to
   stop. Each recording is an episode folder in `dataset/` inside your game directory.

## Converting and publishing

```sh
pip install msed
msed validate .minecraft/dataset
msed convert .minecraft/dataset my-dataset
hf upload your-name/my-dataset my-dataset --repo-type dataset
```

`msed validate` checks every shard, frame count and tick index; `msed convert` validates each
episode again and writes the Parquet dataset with its dataset card. If `msed` is not on your
`PATH`, run `python -m msed` instead.

## Using a dataset

Reading a dataset needs no MSED install: pandas reads it straight from Hugging Face once
`pyarrow` and `huggingface_hub` are installed.

```python
import pandas as pd

ticks = pd.read_parquet(
    "hf://datasets/your-name/my-dataset/ticks/",
    columns=["episode_id", "tick", "player.position.x", "player.position.z", "action.inputs"],
)
```

Each modality is a group of columns, so a model that needs only part of a tick reads only those
columns. Frame `vision.world_frame.frame_index` of a tick is its frame in
`episodes/<episode_id>/world.mp4`.

## Repository

| Folder | Contents |
|---|---|
| [`mod/`](mod) | The Fabric mod (Java 21, Gradle) |
| [`toolkit/`](toolkit) | The `msed` Python package |
| [`proto/`](proto) | The protobuf schema both sides share; every field is documented there |
| [`docs/`](docs) | The documentation site |

[ARCHITECTURE.md](ARCHITECTURE.md) describes how a recording works,
and [CONTRIBUTING.md](CONTRIBUTING.md) how to build and test both parts.

## Citation

MSED was presented at the IEEE Conference on Games 2026 in Madrid. If you use it, please cite the [paper](https://doi.org/10.1109/CoG67430.2026.11778848):

```bibtex
@inproceedings{santos2026msed,
  title     = {{MSED}: Engine-Level Multi-Modal Trajectory Recording for {Minecraft}},
  author    = {da Silva Santos, Rafael and de Paula Barros, Ana Luiza Bessa and de Campos, Gustavo Augusto Lima},
  booktitle = {2026 IEEE Conference on Games (CoG)},
  year      = {2026},
  pages     = {1--8},
  doi       = {10.1109/CoG67430.2026.11778848}
}
```

## License

[MIT](LICENSE). The mod jar bundles [Cloth Config](https://github.com/shedaniel/cloth-config),
under the LGPL-3.0, for its settings screen. Minecraft is a trademark of Mojang Synergies AB; MSED
is not affiliated with Mojang or Microsoft.
