package io.github.rifa456.msed.recording;

import io.github.rifa456.msed.episode.ShardQueue;
import io.github.rifa456.msed.observation.Vectors;
import io.github.rifa456.msed.observation.vision.VisionCollector;
import io.github.rifa456.msed.proto.v1.RenderedCamera;
import io.github.rifa456.msed.proto.v1.TickData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pairs each tick with exactly one frame in each video stream, through two single-use flags.
 *
 * <p>A collected tick sets the world flag. The first render after it consumes that flag before
 * the hand is drawn, captures the world-only frame and sets the GUI flag. The end of the same
 * frame consumes the GUI flag and captures the composited frame. A tick whose world flag is still
 * set when the next tick starts had no render: its frame repeats the previous one, and a warning
 * is logged.
 *
 * <p>The client thread and the render thread are the same thread in Minecraft. Call every method
 * from it.
 */
final class FrameSync {
    private static final Logger LOG = LoggerFactory.getLogger(FrameSync.class);

    private final VisionCollector vision;
    private final ShardQueue shards;
    private boolean worldFlag;
    private boolean guiFlag;
    /** The tick waiting for its frame. It is written once the frame's camera pose is known. */
    private @Nullable TickData pendingTick;

    FrameSync(VisionCollector vision, ShardQueue shards) {
        this.vision = vision;
        this.shards = shards;
    }

    /**
     * Holds a collected tick until its frame renders, then writes it to the shards. Call
     * {@link #flush} before collecting the tick, so a repeated frame is counted before the tick
     * reads its frame index.
     *
     * @param tick the collected tick record, without a camera pose
     */
    void submit(TickData tick) {
        pendingTick = tick;
        worldFlag = true;
    }

    /**
     * Consumes the world flag, at the point of a frame where the world is drawn but the hand and
     * HUD are not. On success, writes the pending tick with this frame's camera pose.
     *
     * @return {@code true} if this frame is the pending tick's frame and must be captured
     */
    boolean consumeWorldFlag() {
        if (!worldFlag) {
            return false;
        }
        worldFlag = false;
        guiFlag = true;
        write(renderedCamera(Minecraft.getInstance()));
        return true;
    }

    /**
     * Consumes the GUI flag, at the end of a frame, once the HUD and any screen are drawn.
     *
     * @return {@code true} if this frame belongs to the latest tick and its composited image must
     *     be captured
     */
    boolean consumeGuiFlag() {
        if (!guiFlag) {
            return false;
        }
        guiFlag = false;
        return true;
    }

    /**
     * Closes out the previous tick before a new one is collected. If that tick never rendered,
     * each stream repeats its previous frame and the tick is written without a camera pose.
     *
     * @param tick the number of the previous tick, for the warning
     */
    void flush(long tick) {
        if (worldFlag) {
            LOG.warn("No frame rendered for tick {}; repeating the previous frame", tick);
            vision.repeatLastFrame();
            worldFlag = false;
            guiFlag = false;
        }
        write(null);
    }

    private void write(@Nullable RenderedCamera camera) {
        TickData tick = pendingTick;
        if (tick == null) {
            return;
        }
        pendingTick = null;
        if (camera != null) {
            tick = tick.toBuilder()
                    .setVision(tick.getVision().toBuilder().setCamera(camera))
                    .build();
        }
        shards.submit(tick);
    }

    /** Returns the camera pose of the frame being rendered now. */
    private static RenderedCamera renderedCamera(Minecraft client) {
        Camera camera = client.gameRenderer.getMainCamera();
        return RenderedCamera.newBuilder()
                .setPosition(Vectors.of(camera.position()))
                .setYaw(camera.yRot())
                .setPitch(camera.xRot())
                .setPartialTick(client.getDeltaTracker().getGameTimeDeltaPartialTick(false))
                .build();
    }
}
