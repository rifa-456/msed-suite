# Using a dataset

A converted dataset is plain Parquet, JSON and MP4. Reading it needs no MSED install.

## Loading the ticks

=== "pandas"

    ```python
    import pandas as pd

    ticks = pd.read_parquet(
        "hf://datasets/your-name/my-dataset/ticks/",
        columns=["episode_id", "tick", "player.position.x", "player.position.z", "action.inputs"],
        dtype_backend="pyarrow",
    )
    ```

    `dtype_backend="pyarrow"` keeps the dataset's types. Without it, pandas turns the integers
    inside list columns, such as event fields and ids, into floats.

    `hf://` paths need `pip install pandas pyarrow huggingface_hub`.

=== "Hugging Face datasets"

    ```python
    from datasets import load_dataset

    ticks = load_dataset("your-name/my-dataset", "ticks", split="train")
    episodes = load_dataset("your-name/my-dataset", "episodes", split="train")
    ```

=== "PyArrow"

    ```python
    import pyarrow.parquet as pq

    table = pq.read_table("my-dataset/ticks/", columns=["tick", "vision.world_frame.frame_index"])
    ```

Each episode is one file in `ticks/`, sorted by tick. A tick's structured state is flattened into
dotted columns such as `player.position.x`, so each modality is a group of columns and a model
reads only the ones it needs. Lists, such as the events or the nearby entities, are list columns
of structs. [Tick columns](columns.md) lists all of them.

`episodes.parquet` holds one row per episode: its id, the MSED and Minecraft versions, the world
seed, start and end times, the tick range, the paths of its files, and the settings it was
recorded with as JSON.

## Empty values

| Value | Meaning |
|---|---|
| `null` | The value is absent: a modality turned off, an unset message, a `oneof` member not chosen |
| `-1` | An id with no value, such as the attacker of fall damage |
| `0` | A real zero; for an item or block id, air, which is also how an empty slot is recorded |

## Ids and the vocabulary

Blocks, items, entities, sounds, effects, screens and events are stored as integer ids. Each
episode's `vocabulary.json` maps them to names:

```python
import json

with open("my-dataset/episodes/20261123_114236_399/vocabulary.json") as f:
    vocabulary = json.load(f)

item_names = {int(i): name for i, name in vocabulary["items"]["entries"].items()}
item_names[0]  # "minecraft:air"
```

`field_index` in the same file names the category of every id column. Registry ids (blocks,
items, entities, sounds, effects) are the same in every episode recorded with the same game
version and mod list; when you combine episodes recorded with different mod lists, map each
through its own vocabulary.

## Video frames

`vision.world_frame.frame_index` is the frame of that tick in the episode's `world.mp4`, and
`vision.gui_frame.frame_index` its frame in `gui.mp4`. Every tick has exactly one frame per
video, so for tick *n* the index is *n* − 1.

```python
import cv2

video = cv2.VideoCapture("my-dataset/episodes/20261123_114236_399/world.mp4")
video.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
ok, frame = video.read()
```

`vision.camera` holds the pose of the camera in that frame. Mouse look applies every frame and
movement is interpolated between ticks, so this pose matches the image more closely than the
player's position and rotation at the end of the tick. A tick without `vision.camera` had no render
of its own: its frames repeat the previous tick's.

## Ray grids

`vision.non_transparent_rays` and `vision.transparent_rays` are grids of rays cast from the
camera, stored as little-endian arrays in bytes columns. Row 0 is the top of the view and column
0 the left, as in the video:

```python
import numpy as np

row = ticks.iloc[0]
grid = "vision.non_transparent_rays"
shape = (row[f"{grid}.height"], row[f"{grid}.width"])
block_ids = np.frombuffer(row[f"{grid}.block_id_raw"], dtype="<i4").reshape(shape)
distances = np.frombuffer(row[f"{grid}.distance_meters"], dtype="<f4").reshape(shape)
```

A ray that hits nothing within the maximum distance has block id 0 (air) and distance -1. The
opaque pass sees through glass, leaves, plants and fluids; the visual pass stops at the first
thing drawn, glass and water surfaces included.
