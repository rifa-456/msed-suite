package io.github.rifa456.msed.observation.vision;

import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.episode.EpisodeFiles;
import io.github.rifa456.msed.observation.StatTracker;
import io.github.rifa456.msed.observation.TrackingCollector;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.ObservationKey;
import io.github.rifa456.msed.proto.v1.RayGrid;
import io.github.rifa456.msed.proto.v1.RecordingSettings;
import io.github.rifa456.msed.proto.v1.VideoRef;
import io.github.rifa456.msed.proto.v1.VisibleBlock;
import io.github.rifa456.msed.proto.v1.VisionSettings;
import io.github.rifa456.msed.proto.v1.VisionState;
import io.github.rifa456.msed.video.VideoEncoder;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The vision modality: for each recorded video stream the index of this tick's frame, and as the
 * configuration selects, the two ray grids and the blocks they hit.
 *
 * <p>The world stream shows the world without the hand and HUD; the GUI stream shows the complete
 * frame. Each tick gets exactly one frame per stream. The render hooks call
 * {@link #capturePureWorld} and {@link #captureGuiWorld}, and a tick without a render calls
 * {@link #repeatLastFrame} instead.
 *
 * <p>The visible blocks are the distinct blocks hit by either ray grid, nearest first, with
 * positions relative to the block that holds the camera. The largest number of visible blocks in
 * one tick of the session goes into the episode metadata.
 */
public final class VisionCollector extends TrackingCollector<VisionState> {

    private final RayGridCollector rayGridCollector = new RayGridCollector();
    private final StatTracker uniqueBlockTracker;
    private @Nullable FrameStream worldStream;
    private @Nullable FrameStream guiStream;

    /** Creates a collector with no video streams that measures the visible-block count of each tick. */
    public VisionCollector() {
        this.uniqueBlockTracker = registerTracker(ObservationKey.OBSERVATION_KEY_VISION_MAX_VISIBLE_BLOCKS);
    }

    /**
     * Starts a frame stream for each encoder given. Call it on the render thread when a recording
     * starts.
     *
     * @param world the encoder of the world stream, or {@code null} when it is not recorded
     * @param gui the encoder of the GUI stream, or {@code null} when it is not recorded
     */
    public void setEncoders(@Nullable VideoEncoder world, @Nullable VideoEncoder gui) {
        this.worldStream = world != null ? new FrameStream("World", world, world.width(), world.height()) : null;
        this.guiStream = gui != null ? new FrameStream("Gui", gui, gui.width(), gui.height()) : null;
    }

    @Override
    public String getProfilerName() {
        return "msed:collectVisionState";
    }

    /**
     * Hands every pending frame to the encoders and closes both streams. Call it on the render
     * thread before shutting the encoders down.
     */
    public void finishVideo() {
        if (worldStream != null) worldStream.finish();
        if (guiStream != null) guiStream.finish();
        worldStream = null;
        guiStream = null;
    }

    /**
     * Captures the current render as this tick's world frame, after the world is drawn and before
     * the hand and HUD. Does nothing when the world stream is not recorded. Render thread only.
     *
     * @param client the client whose main render target is read
     */
    public void capturePureWorld(Minecraft client) {
        if (worldStream != null) worldStream.capture(client);
    }

    /**
     * Captures the complete frame, HUD included, as this tick's GUI frame. Does nothing when the
     * GUI stream is not recorded. Render thread only.
     *
     * @param client the client whose main render target is read
     */
    public void captureGuiWorld(Minecraft client) {
        if (guiStream != null) guiStream.capture(client);
    }

    /** Gives the current tick a copy of each stream's previous frame, for a tick without a render. */
    public void repeatLastFrame() {
        if (worldStream != null) worldStream.repeat();
        if (guiStream != null) guiStream.repeat();
    }

    @Override
    protected VisionState collect(Minecraft client, ProfilerFiller profiler) {
        MsedConfig config = ConfigFile.getConfig();
        RecordingSettings.Vision visionRecordingConfig = config.getRecording().getRecordVision();
        VisionSettings visionConfig = config.getVision();
        VisionState.Builder builder = VisionState.newBuilder();
        if (worldStream != null) {
            builder.setWorldFrame(VideoRef.newBuilder()
                    .setFileName(EpisodeFiles.WORLD_VIDEO)
                    .setFrameIndex(worldStream.nextFrameIndex()));
        }
        if (guiStream != null) {
            builder.setGuiFrame(VideoRef.newBuilder()
                    .setFileName(EpisodeFiles.GUI_VIDEO)
                    .setFrameIndex(guiStream.nextFrameIndex()));
        }
        if (!visionRecordingConfig.getRecordNonTransparentRayMatrix()
                && !visionRecordingConfig.getRecordTransparentRayMatrix()
                && !visionRecordingConfig.getRecordUniqueBlocks()) {
            return builder.build();
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return builder.build();
        Map<BlockPos, Float> hitBlocks = new HashMap<>();
        if (visionRecordingConfig.getRecordNonTransparentRayMatrix() || visionRecordingConfig.getRecordUniqueBlocks()) {
            profiler.push("non_transparent_pass");
            RayGrid grid = rayGridCollector.collect(
                    client,
                    visionConfig.getNonTransparentRayGridWidth(),
                    visionConfig.getNonTransparentRayGridHeight(),
                    visionConfig.getMaxRayDistance(),
                    false,
                    hitBlocks);
            if (visionRecordingConfig.getRecordNonTransparentRayMatrix()) {
                builder.setNonTransparentRays(grid);
            }
            profiler.pop();
        }
        if (visionRecordingConfig.getRecordTransparentRayMatrix() || visionRecordingConfig.getRecordUniqueBlocks()) {
            profiler.push("transparent_pass");
            RayGrid grid = rayGridCollector.collect(
                    client,
                    visionConfig.getTransparentRayGridWidth(),
                    visionConfig.getTransparentRayGridHeight(),
                    visionConfig.getMaxRayDistance(),
                    true,
                    hitBlocks);
            if (visionRecordingConfig.getRecordTransparentRayMatrix()) {
                builder.setTransparentRays(grid);
            }
            profiler.pop();
        }
        if (visionRecordingConfig.getRecordUniqueBlocks()) {
            profiler.push("unique_blocks");
            List<Map.Entry<BlockPos, Float>> sortedBlocks = new ArrayList<>(hitBlocks.entrySet());
            sortedBlocks.sort(Map.Entry.comparingByValue());
            uniqueBlockTracker.observe(sortedBlocks.size());
            Vocabulary vocab = Vocabulary.getInstance();
            Camera camera = client.gameRenderer.getMainCamera();
            Vec3 cameraPos = camera.position();
            int px = (int) Math.floor(cameraPos.x);
            int py = (int) Math.floor(cameraPos.y);
            int pz = (int) Math.floor(cameraPos.z);
            for (Map.Entry<BlockPos, Float> entry : sortedBlocks) {
                BlockPos pos = entry.getKey();
                BlockState state = client.level.getBlockState(pos);
                int blockId = vocab.getId(state.getBlock());
                builder.addVisibleBlocks(VisibleBlock.newBuilder()
                        .setBlockId(blockId)
                        .setRelX(pos.getX() - px)
                        .setRelY(pos.getY() - py)
                        .setRelZ(pos.getZ() - pz)
                        .setDistance(entry.getValue())
                        .build());
            }
            profiler.pop();
        }
        return builder.build();
    }
}
