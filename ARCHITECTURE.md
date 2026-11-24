# Architecture

This document describes how MSED records and converts gameplay. Building, testing and the code
conventions are in [CONTRIBUTING.md](https://github.com/rifa-456/msed-suite/blob/main/CONTRIBUTING.md). The design follows the paper
[*MSED: Engine-Level Multi-Modal Trajectory Recording for Minecraft*](https://doi.org/10.1109/CoG67430.2026.11778848)
(IEEE CoG 2026).

## Overview

The mod runs inside the Minecraft client. At the end of every client tick it reads each modality
straight from the game's memory, adds the events logged since the previous tick, and hands the
tick record to a writer thread, which appends it to a shard file. Two video streams are captured
from the render pipeline, one frame per tick, and encoded by an FFmpeg process. When the
recording stops, the mod writes the episode's vocabulary and its manifest.

The toolkit runs later, outside the game. It checks each episode folder and turns it into Parquet
files whose columns come from the protobuf schema in `proto/`, the schema the mod records with.

## One tick

What happens during one tick of a recording:

1. Minecraft finishes its client tick and Fabric calls `MsedClient`'s tick handler on the client
   thread. `Recorder` reads the record key, runs the start countdown, and calls `Session.tick`
   unless the game is paused.
2. `FrameSync.flush` checks the previous tick: if no frame was rendered for it, its frame repeats
   the last one, with a warning naming the tick, and the previous tick record is written.
3. `DataCollector.collectTick` runs every collector once, each in its own profiler section: the
   action (the inputs `InputRecorder` buffered since the previous tick), vision, hearing (the
   sounds `HearingCollector` queued), player, inventory and GUI. It then takes every event logged
   since the previous collection and stamps it with this tick's number.
4. Without video, the record goes straight to `ShardQueue`. With video, `FrameSync.submit` sets the
   first capture flag and the record waits for its frame.
5. The next render consumes that flag in the hook `GameRendererMixin` places after the world is
   drawn and before the hand. The record is written with the pose of the camera this render used,
   and `VisionCollector` captures the world-only frame. The second flag is consumed at the end of
   the same frame, after the HUD, where the GUI frame is captured. Each flag is used once, so a
   tick gets at most one frame per stream however many frames render.
6. `RgbCapture` scales the frame into its own framebuffer and starts an asynchronous read into
   one of two pixel buffer objects; the bytes are collected a frame later. `FrameStream` keeps one
   frame per tick in order and hands it to `VideoEncoder`, whose submitter thread feeds FFmpeg
   through `FramePipe`.
7. `ShardQueue`'s writer thread appends the record to the current shard with `ShardWriter` and
   starts a new shard every `ticks_per_shard` ticks. A full queue drops the tick and logs its
   number; it never blocks the game.

Events reach the queue in step 3 from any thread. Hooks in `mixin/events` log them as the game
raises them. Damage and item pickups are decided by the integrated server of a singleplayer game,
so `PlayerMixin` and `ItemEntityMixin` run there and send a packet that `MsedClient` turns into
an event on the client thread.

When the record key stops the recording, `Session.stop` writes one last tick that holds the
events logged after the final regular tick, the stop event included, finishes the videos in the
background, and writes `vocabulary.json` and `metadata.json`.

## Inputs and action set closure

Every state change in a recording comes from an input that is recorded. `KeyboardHandlerMixin`
and `MouseHandlerMixin` see each key, button, scroll and character where Minecraft first receives
it. `InputPolicy` drops the inputs that have no gameplay effect, such as chat, screenshots, the
camera perspective key and F3, before the game sees them; everything else is recorded by
`InputRecorder` and passed on. Inside screens, letter keys always pass, so typing on a sign works.
Two more hooks close the remaining paths: `ScreenMixin` keeps Esc from closing screens, and
`LocalPlayerMixin` turns off sprinting by double-tapping forward, so sprint comes only from its
binding.

The paper describes key suppression at the key mapping's consumption call. Several keys, F1 and
F3 among them, are handled by Minecraft before that call, so MSED blocks at the keyboard handler
instead, which covers them all.

## Code map

### `mod/`

The Fabric mod, in `io.github.rifa456.msed`. Its packages form layers; a package uses only the
layers below it, and `ArchitectureTest` checks this. From the top:

1. `MsedClient`: the client entry point, which connects the recorder to the game's events.
2. `mixin`: the hooks into Minecraft's classes, one subpackage per package they serve. A hook
   calls MSED code and holds no logic of its own.
3. `debug`: hotkeys for overlays that preview the ray grids, the visible blocks and the nearby
   entities, and a minimal render. They work only while not recording.
4. `recording`: the record key, the countdown, `Session`, the frame protocol in `FrameSync`, and
   `DataCollector`, which builds each tick record.
5. `action`: the input policy, the input buffer and the action collector.
6. `observation`: one collector per modality, and `vision/` with the ray grids and the frame
   capture.
7. `events`, `video` and `vocabulary`: the packets the integrated server sends, FFmpeg discovery
   and encoding, and the id tables.
8. `episode`: the episode folder's file names, the shard writer and its queue, and the manifest.
9. `config`: `msed.json` and the settings screen.

`Msed` holds the mod's id and version and is used by every layer.

A new observation modality is a message in `proto/msed/v1`, a collector that extends
`AbstractCollector`, and three lines in `DataCollector`: a field, an entry in its collector list
and a line in `collectTick`. The writer, the session and the toolkit need no change.

The paper reads the game's OpenGL texture id through reflection. In the released mod the field
names are obfuscated, so `RgbCapture` casts the texture to Minecraft's `GlTexture` and calls its
public `glId()` instead; on a render backend other than OpenGL it fails on first use, as the
reflection did.

### `toolkit/`

The `msed` package. Its public API is what `msed/__init__.py` exports, and `cli.py` is the
`msed` command; everything in a folder starting with `_` is internal. The folders form layers,
and `lint-imports` checks that each uses only the ones below it. From the top:

1. `_dataset`: builds the Parquet dataset. `convert` validates each episode, writes its ticks
   with `columns` and adds its row to `episodes.parquet` through `index`; an episode's row is
   written last, so a stopped run resumes where it left off. `columns` builds the Arrow schema
   from the `TickData` descriptor on every run, so a field added to the schema becomes a column
   without a change in the toolkit.
2. `_validation`: `checks` tests an episode for shard sizes and SHA-256 against the manifest,
   readable records, an increasing tick sequence and frame indices within each video, and
   collects what it finds in a `report`.
3. `_episode`: reads a raw episode folder: the manifest (`folder`), the shards, the videos'
   frame counts and the vocabulary.
4. `v1`, `errors` and `_version`: the generated protobuf code, the toolkit's own exception and
   its version.

The build hook in `hatch_build.py` compiles `proto/` into `msed.v1` on every build.

### `proto/`

The schema of everything an episode holds: the tick record and its modalities, the events, the
manifest, the vocabulary and the config. Every field states its unit, range and empty value.
Field options mark which fields hold vocabulary ids and give the config screen its limits.

### Elsewhere

- `docs/`: the documentation site (MkDocs) and the brand assets.
- `.github/workflows/`: CI for the mod and the toolkit.
