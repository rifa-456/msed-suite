# Your first dataset

In this tutorial you record half a minute of play, check it, turn it into a dataset, and look
inside: the player's path, the moment a block broke, and the video frame of that moment. It takes
about fifteen minutes.

You need Minecraft Java Edition {{ minecraft_version }} and Python {{ python_version }} or newer.

## 1. Record an episode

1. Install the mod and FFmpeg as [Recording](recording.md#installing) describes, and start the
   game.
2. Create a singleplayer world in Survival mode, or open one.
3. Press **F12** twice, quickly. A countdown appears in the top-left corner, then chat says
   *Recording started*.
4. Walk a few steps, break one block with your hand, pick up the item it drops, and open your
   inventory with **E** and close it again.
5. Press **F12** twice again. Chat says *Recording stopped*, and a moment later that each video
   is finalized.

The recording is now a folder in `dataset` inside your game directory, named after the moment it
started, such as `dataset/20261123_114236_399`.

=== "Windows"

    The game directory is `%APPDATA%\.minecraft` unless your launcher sets another one.

=== "macOS"

    The game directory is `~/Library/Application Support/minecraft` unless your launcher sets
    another one.

=== "Linux"

    The game directory is `~/.minecraft` unless your launcher sets another one.

## 2. Check it

Install the toolkit, then open a terminal in the game directory:

```sh
pip install msed
msed validate dataset
```

```text
OK  20261123_114236_399  (612 ticks, ticks 1..612)

1/1 episode(s) valid
```

612 ticks is 30.6 seconds: the game runs 20 ticks per second, and each tick is one record. Your
number will differ. A warning here is not a failure; [Converting](converting.md#validating-episodes)
explains each one.

## 3. Convert it

```sh
msed convert dataset my-first-dataset
```

`my-first-dataset` is now a dataset ready for Hugging Face: one Parquet file of ticks per
episode, an index of the episodes, and the videos next to them.

## 4. Look inside

Install pandas and OpenCV, then start Python in the folder that holds `my-first-dataset`:

```sh
pip install pandas pyarrow opencv-python
```

Load the ticks and look at the first ones:

```python
import pandas as pd

ticks = pd.read_parquet("my-first-dataset/ticks/", dtype_backend="pyarrow")
ticks[["tick", "player.position.x", "player.position.z", "player.health"]].head()
```

`dtype_backend="pyarrow"` keeps every number as the dataset stores it; without it, pandas
turns the integers inside events and lists into floats.

Each row is one tick. `player.position.x` and `player.position.z` are where the player's feet
were, in blocks; `player.health` is in half hearts, so 20 is full health. The other columns are
listed in [Tick columns](columns.md).

Every event lands in the tick it happened in. Find the block you broke:

```python
for index, events in ticks["events"].items():
    for event in events:
        if event["payload"] == "block_break":
            broken = ticks.loc[index]
            block_id = event["block_break"]["block_id"]
            print(broken["episode_id"], broken["tick"], block_id)
```

`broken` is the whole row of that tick, so everything below comes from the same episode and the
same moment, however many episodes the dataset holds.

`block_id` is a number. The episode's vocabulary names it:

```python
import json

episode_id = broken["episode_id"]
with open(f"my-first-dataset/episodes/{episode_id}/vocabulary.json") as f:
    vocabulary = json.load(f)

vocabulary["blocks"]["entries"][str(block_id)]  # for example "minecraft:grass_block"
```

Finally, save the frame of that tick from the GUI video, which shows what you saw:

```python
import cv2

video = cv2.VideoCapture(f"my-first-dataset/episodes/{episode_id}/gui.mp4")
video.set(cv2.CAP_PROP_POS_FRAMES, int(broken["vision.gui_frame.frame_index"]))
ok, frame = video.read()
cv2.imwrite("block_break.png", frame)
```

`block_break.png` shows the game at the tick the block broke. Each tick has exactly one frame in
each video, so a row and its frame always belong together.

## Next

- [How MSED records](concepts.md) explains why a tick and its frame line up, and why every
  player action is in the data.
- [Using a dataset](dataset.md) covers the ray grids, the empty values and loading a dataset
  from Hugging Face.
- [Converting and publishing](converting.md#publishing-on-hugging-face) uploads the dataset.
