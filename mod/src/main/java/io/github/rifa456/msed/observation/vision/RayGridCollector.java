package io.github.rifa456.msed.observation.vision;

import com.google.protobuf.ByteString;
import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.proto.v1.DType;
import io.github.rifa456.msed.proto.v1.RayGrid;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The caster of ray grids. A grid is {@code width} by {@code height} rays from the camera through
 * points spread over the view, and records for each ray the vocabulary id of the block it hits
 * and the distance to the hit.
 *
 * <p>When the configured foveation exponent is not 1, each point's coordinates in {@code [-1, 1]}
 * become {@code sign(x) * |x|^exponent}, so an exponent above 1 packs the rays toward the center
 * of the view.
 *
 * <p>The grid is row-major, row 0 at the top and column 0 at the left, like the video frames.
 * Block ids are little-endian {@code int32} and distances little-endian {@code float32}, in
 * blocks. A ray that hits nothing records the air block and the distance {@code -1}.
 */
public final class RayGridCollector {

    /** A receiver of every ray of a pass. The debug overlay draws rays with it; recording does not. */
    @FunctionalInterface
    public interface RayListener {
        /**
         * Receives one ray.
         *
         * @param start the camera position the ray starts at
         * @param end the hit point, or the end of the ray on a miss
         * @param hit whether the ray hit a block
         */
        void onRay(Vec3 start, Vec3 end, boolean hit);
    }

    /**
     * Casts one grid of rays from the camera, without reporting single rays. Call it on the client
     * thread.
     *
     * @param client the client whose camera and level are used
     * @param width the number of rays per row
     * @param height the number of rows
     * @param maxDist the length of each ray, in blocks
     * @param isTransparent whether rays stop at the first visible surface, fluids included, rather
     *     than at the first block that blocks sight
     * @param hitBlocks the distance to each block hit so far, which this pass adds to; each block
     *     keeps the distance of its nearest hit
     * @return the grid, or an empty grid when there is no player or level
     */
    public RayGrid collect(
            Minecraft client,
            int width,
            int height,
            float maxDist,
            boolean isTransparent,
            Map<BlockPos, Float> hitBlocks) {
        return collect(client, width, height, maxDist, isTransparent, hitBlocks, null);
    }

    /**
     * Casts one grid of rays from the camera and reports each ray to {@code listener}. Call it on
     * the client thread.
     *
     * @param client the client whose camera and level are used
     * @param width the number of rays per row
     * @param height the number of rows
     * @param maxDist the length of each ray, in blocks
     * @param isTransparent whether rays stop at the first visible surface, fluids included, rather
     *     than at the first block that blocks sight
     * @param hitBlocks the distance to each block hit so far, which this pass adds to; each block
     *     keeps the distance of its nearest hit
     * @param listener the receiver of every ray, or {@code null} for none
     * @return the grid, or an empty grid when there is no player or level
     */
    public RayGrid collect(
            Minecraft client,
            int width,
            int height,
            float maxDist,
            boolean isTransparent,
            Map<BlockPos, Float> hitBlocks,
            @Nullable RayListener listener) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            return RayGrid.getDefaultInstance();
        }
        float exponent = ConfigFile.getConfig().getVision().getFoveationExponent();
        Vocabulary vocab = Vocabulary.getInstance();
        boolean isLinear = Math.abs(exponent - 1.0f) < 0.001f;
        int size = width * height;
        ByteBuffer idBuffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer distBuffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN);
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        Camera.NearPlane projection = camera.getNearPlane();
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                // Row 0 is the top and column 0 the left, to match the video frames.
                float linearFactorX = width > 1 ? (i / (float) (width - 1)) * 2.0f - 1.0f : 0.0f;
                float linearFactorY = height > 1 ? 1.0f - (j / (float) (height - 1)) * 2.0f : 0.0f;
                float factorX, factorY;
                if (isLinear) {
                    factorX = linearFactorX;
                    factorY = linearFactorY;
                } else {
                    factorX = (float) Math.copySign(Math.pow(Math.abs(linearFactorX), exponent), linearFactorX);
                    factorY = (float) Math.copySign(Math.pow(Math.abs(linearFactorY), exponent), linearFactorY);
                }
                Vec3 rayDirection = projection.getPointOnPlane(factorX, factorY);
                Vec3 dir = rayDirection.normalize();
                Vec3 rayEnd = cameraPos.add(dir.scale(maxDist));
                BlockHitResult hitResult = performRaycast(level, player, cameraPos, rayEnd, isTransparent);
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = hitResult.getBlockPos();
                    BlockState state = level.getBlockState(pos);
                    float distance = (float) cameraPos.distanceTo(hitResult.getLocation());
                    hitBlocks.merge(pos, distance, Math::min);
                    idBuffer.putInt(vocab.getId(state.getBlock()));
                    distBuffer.putFloat(distance);
                    if (listener != null) {
                        listener.onRay(cameraPos, hitResult.getLocation(), true);
                    }
                } else {
                    idBuffer.putInt(vocab.getAirBlockId());
                    distBuffer.putFloat(-1.0f);
                    if (listener != null) {
                        listener.onRay(cameraPos, rayEnd, false);
                    }
                }
            }
        }
        return RayGrid.newBuilder()
                .setWidth(width)
                .setHeight(height)
                .setBlockIdRaw(ByteString.copyFrom(idBuffer.array()))
                .setBlockIdDtype(DType.D_TYPE_INT32)
                .setDistanceMeters(ByteString.copyFrom(distBuffer.array()))
                .setDistanceDtype(DType.D_TYPE_FLOAT32)
                .build();
    }

    /**
     * Casts one ray. The transparent pass stops at the first visible thing: any block outline,
     * fluid surfaces included. The other pass stops at the first block that occludes sight, so
     * glass, leaves, plants and fluids are seen through. A miss is a {@code MISS} result at the
     * end of the ray.
     */
    private static BlockHitResult performRaycast(
            ClientLevel level, LocalPlayer player, Vec3 start, Vec3 end, boolean isTransparent) {
        if (isTransparent) {
            return level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        }
        return BlockGetter.traverseBlocks(
                start,
                end,
                level,
                (lvl, pos) -> {
                    BlockState state = lvl.getBlockState(pos);
                    if (!state.canOcclude()) return null;
                    return lvl.clipWithInteractionOverride(start, end, pos, state.getShape(lvl, pos), state);
                },
                lvl -> BlockHitResult.miss(
                        end, Direction.getApproximateNearest(end.subtract(start)), BlockPos.containing(end)));
    }
}
