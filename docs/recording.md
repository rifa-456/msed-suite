---
description: Install the MSED mod for Minecraft with Fabric and FFmpeg, record gameplay episodes with one key, and see which inputs are blocked while recording.
---

# Recording

## Installing

MSED is a mod for Minecraft Java Edition {{ minecraft_version }}, loaded by Fabric. You need the
game, bought and installed with the official launcher, and about ten minutes.

1. Install Fabric: download the [Fabric installer](https://fabricmc.net/use/installer/), run it,
   choose Minecraft {{ minecraft_version }}, and click *Install*. The launcher now has a
   *fabric-loader-{{ minecraft_version }}* profile; start it once and close the game, so the
   `mods` folder is created.
2. Download these two files:
    - [`msed-{{ mod_version }}.jar`](https://github.com/rifa-456/msed-suite/releases/latest), the
      mod;
    - [`fabric-api-{{ fabric_api_version }}.jar`]({{ fabric_api_url }}), the library it needs, in
      the version MSED is built against.
3. Put both files in the `mods` folder of your game directory:

    === "Windows"

        `%APPDATA%\.minecraft\mods`

    === "macOS"

        `~/Library/Application Support/minecraft/mods`

    === "Linux"

        `~/.minecraft/mods`

4. Install FFmpeg, which encodes the videos:

    === "Windows"

        ```powershell
        winget install ffmpeg
        ```

    === "macOS"

        ```sh
        brew install ffmpeg
        ```

    === "Linux"

        ```sh
        sudo apt install ffmpeg
        ```

        or your distribution's package.

5. Start the game with the Fabric profile. MSED finds FFmpeg on `PATH` and in the usual install
   folders of winget, Scoop, Chocolatey and Homebrew, then tests the encoders once in the
   background, hardware encoders first.

Without FFmpeg, MSED records every modality except the videos and says so in chat when a
recording starts.

## Recording an episode

Open a singleplayer world and press **F12** twice within half a second; outside a world the key
does nothing. A countdown appears in
the top-left corner; when it ends, chat shows *Recording started*. Press **F12** twice again to
stop. Each recording is one episode: a folder in `dataset/` inside your game directory, named
after its start time, such as `dataset/20261123_114236_399/`.

```text
20261123_114236_399/
├── metadata.json      the manifest: versions, times, settings, shards
├── vocabulary.json    the name of every id
├── shard_0000.bin     tick records, 1000 per shard by default
├── world.mp4          world-only video, one frame per tick
└── gui.mp4            the final image, HUD and screens included
```

While the videos finish encoding after a stop, the game keeps running; chat reports each video
when it is complete.

### While recording

Every state change in a recording comes from an input that is recorded. MSED therefore blocks
the inputs that have no gameplay effect until the recording stops:

- chat and commands, the player list, social interactions, and advancements;
- screenshots, the camera perspective, smooth camera, fullscreen, and hiding the HUD (F1);
- the debug screen (F3) and every F3 combination;
- pick block (middle click), saving and loading hotbars, and the spectator keys;
- MSED's own preview keys and settings key.

When the recording starts, the view is reset to what the player can keep: the HUD is shown,
the debug screen closes, and the camera returns to first person without smooth camera.

Two other changes keep the inputs complete: Esc no longer closes screens (the inventory key
does, and it is recorded), and double-tapping forward does not sprint (the sprint key does).

## Settings

Press **F7** in the game to open the settings; the key can be changed under *Options → Controls →
Key Binds → MSED*, next to the record key. The settings can also be edited in
`config/msed.json` in your game directory while the game is closed. Changes apply to the next
recording; each episode's `metadata.json` keeps the settings it was recorded with.

[Settings](settings.md) lists every setting with its default and range.

## Previewing what is recorded

When not recording, four keys show what the recorder captures, so you can check a setup before
recording:

| Key | Overlay |
|---|---|
| F6 | Outlines and names of the entities within 16 blocks |
| F8 | Outlines of the blocks the rays hit |
| F9 | The hit point of every ray: green for the opaque pass, cyan for the visual pass |
| F10 | A minimal render: the sky, terrain, particles, clouds and weather are not drawn |

All four turn off while a recording runs.
