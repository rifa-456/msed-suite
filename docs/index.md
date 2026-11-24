---
hide:
  - navigation
  - toc
---

<h1 class="msed-hero" markdown>
![MSED](assets/banner-light.svg#only-light)
![MSED](assets/banner-dark.svg#only-dark)
</h1>

<p class="msed-lead">MSED records Minecraft gameplay from inside the game engine. Each of the
game's 20 ticks per second becomes one record with the player's inputs, the game state, and the
events of that tick, next to two videos with exactly one frame per tick.</p>

It has two parts: a client-side [Fabric](https://fabricmc.net/) mod for Minecraft {{ minecraft_version }} that
records, and `msed`, a Python toolkit that checks the recordings and converts them into a
Parquet dataset for Hugging Face.

<div class="grid cards" markdown>

-   :material-school:{ .lg .middle } __Your first dataset__

    ---

    Record half a minute of play, convert it, and find the frame where a block broke.

    [:octicons-arrow-right-24: Start the tutorial](tutorial.md)

-   :material-lightbulb-on:{ .lg .middle } __How MSED records__

    ---

    Why each tick has exactly one frame, and why every player action is in the data.

    [:octicons-arrow-right-24: Read the concepts](concepts.md)

-   :material-record-rec:{ .lg .middle } __Recording__

    ---

    Install the mod and FFmpeg, then press F12 twice in a world.

    [:octicons-arrow-right-24: Start recording](recording.md)

-   :material-database-arrow-up:{ .lg .middle } __Converting and publishing__

    ---

    Check recordings with `msed validate`, turn them into a dataset with `msed convert`, upload
    it to Hugging Face.

    [:octicons-arrow-right-24: Convert episodes](converting.md)

-   :material-table-eye:{ .lg .middle } __Using a dataset__

    ---

    Read a published dataset with pandas or `datasets`, without installing MSED: columns, ids,
    video frames and ray grids.

    [:octicons-arrow-right-24: Read a dataset](dataset.md)

-   :material-file-tree:{ .lg .middle } __Reference__

    ---

    Every column and schema field, with its unit, range and empty value.

    [:octicons-arrow-right-24: Browse the columns](columns.md)

</div>

## Contents of a tick

| Modality | What is recorded |
|---|---|
| Action | Every key, mouse button, scroll and typed character, with camera movement and the gameplay bindings held at the end of the tick |
| Vision | A world-only and a GUI-composited video frame, the camera pose of that frame, and two ray grids with the block id and distance per ray |
| Player | Position, velocity, rotation, health, hunger, air, experience, status effects, combat and mining state, and the entities within 16 blocks |
| Inventory | Hotbar, main inventory, armor and offhand |
| GUI | The open screen, every slot with its item, the cursor item and the hovered slot |
| Hearing | Every sound the game plays, with its volume and position |
| Events | Damage, deaths, pickups, drops, block breaks, eating, sleeping, respawns, advancements, and screens opening and closing |

Each tick's frames are captured at fixed points of the render, so tick *n* owns frame *n* − 1 of
each video by construction. Ids for blocks, items, entities, sounds and effects come from the
game's registries and stay the same across sessions with the same game version and mod list.

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
