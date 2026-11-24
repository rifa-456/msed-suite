---
description: How MSED pairs each Minecraft tick with exactly one video frame, records every player action, and keeps ids stable across recordings.
---

# How MSED records

MSED runs inside the Minecraft client and reads each modality from the game's own state, at the
end of every client tick. This page explains what that gives a dataset, and where its limits
are. [Architecture](architecture.md) describes the same design from the code's side.

## One record per tick

Minecraft advances its world in ticks, 20 per second. After the client finishes a tick, MSED
reads the player's inputs, the player, the inventory, the open screen, the sounds and the ray
grids, and writes them as one tick record. Tick numbers start at 1 and count up by one. A paused
game advances no ticks, so it records none, and the numbering continues without a gap when it
resumes.

Reading the game's memory, instead of the screen, gives exact values: health in half hearts, a
position to the fraction of a block, the item in every slot. No vision model or screen parsing
stands between the game and the data.

## Frames that belong to their tick

Each tick also gets one frame in each of two videos: `world.mp4`, drawn before the hand and the
HUD, and `gui.mp4`, the image the player saw. Rendering and ticking run at different rates, so a
frame cannot simply be taken every 50 milliseconds. MSED pairs them instead:

1. When a tick record is collected, it sets a flag.
2. The next render clears that flag as soon as the world is drawn, captures the world-only frame,
   and sets a second flag.
3. The end of the same render clears the second flag and captures the final image.

Each flag is cleared once, so a tick gets at most one frame per video however many frames
render. The tick record stores each frame's index, and frames are numbered from 0, so tick *n*
holds frame *n* − 1. It also stores `vision.camera`, the camera pose of that render, which
matches the image more closely than the player's pose at the end of the tick.

When no frame renders before the next tick, the tick that missed its render gets a copy of the
previous frame, and no camera pose, which is how `msed validate` finds it. No tick is left
without a frame and no frame is skipped, so the indices never drift.

## Every player action is recorded

A dataset of play is most useful when everything the player did can be traced to an input in
the same data. MSED records every key, mouse button, scroll and typed character where the game
first receives it, in arrival order, plus the mouse movement of each tick and the gameplay
bindings held at its end.

Some inputs have no effect on the game but change the image or interrupt play: the chat, the F3
debug screen, the camera perspective, screenshots. While recording, MSED blocks those, so the
player can act only through inputs that are recorded, and the image shows only their effects. Two shortcuts are also removed:
Esc does not close screens (the inventory key does, and it is recorded), and double-tapping
forward does not sprint (the sprint key does). [Recording](recording.md#while-recording) lists
every blocked input.

## Events, as they happen

Some things happen between ticks: a block breaks, an item is picked up, a screen opens, the
player dies. MSED logs each event when the game raises it and stores it in the next tick record,
so an event appears in exactly one tick, in the order the events happened. When the recording
stops, one last tick record holds the events logged after the final regular tick, the stop
event among them. An episode whose last tick has no stop event ended some other way, and
`msed validate` says so.

MSED records events without judging them. There is no reward and no notion of success: every
event of the session is kept, so a dataset can be labelled for any task afterwards.

Damage and item pickups are decided by the game's server. In singleplayer that server runs inside
the client and MSED reads them there; a remote server does not report them, so episodes recorded
on one have no damage or pickup events.

## Ids that stay the same

Blocks, items, entities, sounds and status effects are stored by their id in the game's
registries. These ids are the same in every episode recorded with the same game version and the
same mods, so such episodes can be pooled directly. Values the game does not number, such as
screens and slot roles, get a hash of their name, which is the same on every machine.

Each episode carries its own `vocabulary.json` with the name of every id it can contain. When
episodes come from different game versions or mod lists, map each through its own vocabulary.

## When something goes wrong

While recording, the game never waits for the disk or the video encoder, so problems show up in
the data, not as a slower game:

- When the disk writer falls behind, ticks are dropped and their numbers logged. The tick
  numbers then have gaps.
- When no frame renders for a tick, the frame repeats, as above.
- When the game closes or crashes during a recording, the episode has no `metadata.json`.

`msed validate` finds each of these and reports which ticks they affect.
[Converting](converting.md#validating-episodes) lists every error and warning.
