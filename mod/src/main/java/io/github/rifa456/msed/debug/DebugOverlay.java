package io.github.rifa456.msed.debug;

import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.observation.NearbyEntities;
import io.github.rifa456.msed.observation.vision.RayGridCollector;
import io.github.rifa456.msed.proto.v1.VisionSettings;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Overlays of what the recorder captures: the ray matrix, the visible blocks and the nearby
 * entities. The overlays run the same queries as the collectors and are drawn with Minecraft's
 * gizmos. They pause while recording, so they add no work or visuals to an episode.
 */
public final class DebugOverlay {
    private static final int SOLID_HIT = 0xFF3DDC5A;
    private static final int VISUAL_HIT = 0xFF33C3F0;
    private static final int BLOCK_OUTLINE = 0xFFFFD23F;
    private static final int ENTITY_OUTLINE = 0xFFFF8A3D;

    private final DebugKeys keys;
    private final RayGridCollector rays = new RayGridCollector();

    /**
     * Creates the overlays.
     *
     * @param keys the hotkeys that turn each overlay on or off
     */
    public DebugOverlay(DebugKeys keys) {
        this.keys = keys;
    }

    /**
     * Draws the overlays that are on, once per tick. Call it from the client tick: Minecraft
     * collects the gizmos added during a tick and draws them until the next tick.
     *
     * @param client the client whose player and level are drawn around
     * @param recording whether a recording is running, in which case nothing is drawn
     */
    public void onClientTick(Minecraft client, boolean recording) {
        LocalPlayer player = client.player;
        if (recording || player == null || client.level == null) {
            return;
        }
        if (keys.rayMatrix() || keys.visibleBlocks()) {
            drawRays(client);
        }
        if (keys.nearbyEntities()) {
            drawEntities(player);
        }
    }

    private void drawRays(Minecraft client) {
        VisionSettings vision = ConfigFile.getConfig().getVision();
        boolean showRays = keys.rayMatrix();
        Map<BlockPos, Float> hitBlocks = new HashMap<>();
        // Rays start at the eye, so in first person they collapse to dots; draw the hit points
        // instead, which shows the sampling grid on the surfaces it lands on.
        rays.collect(
                client,
                vision.getNonTransparentRayGridWidth(),
                vision.getNonTransparentRayGridHeight(),
                vision.getMaxRayDistance(),
                false,
                hitBlocks,
                showRays
                        ? (start, end, hit) -> {
                            if (hit) Gizmos.point(end, SOLID_HIT, 4.0f);
                        }
                        : null);
        rays.collect(
                client,
                vision.getTransparentRayGridWidth(),
                vision.getTransparentRayGridHeight(),
                vision.getMaxRayDistance(),
                true,
                hitBlocks,
                showRays
                        ? (start, end, hit) -> {
                            if (hit) Gizmos.point(end, VISUAL_HIT, 6.0f);
                        }
                        : null);
        if (keys.visibleBlocks()) {
            GizmoStyle style = GizmoStyle.stroke(BLOCK_OUTLINE, 2.0f);
            for (BlockPos pos : hitBlocks.keySet()) {
                Gizmos.cuboid(pos, style);
            }
        }
    }

    private void drawEntities(LocalPlayer player) {
        GizmoStyle style = GizmoStyle.stroke(ENTITY_OUTLINE, 2.0f);
        for (Entity entity : NearbyEntities.around(player)) {
            Gizmos.cuboid(entity.getBoundingBox(), style);
            Gizmos.billboardTextOverMob(
                    entity, 0, EntityType.getKey(entity.getType()).getPath(), ENTITY_OUTLINE, 0.5f);
        }
    }
}
