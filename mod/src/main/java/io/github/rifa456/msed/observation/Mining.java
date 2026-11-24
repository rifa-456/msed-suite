package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.mixin.observation.MultiPlayerGameModeAccessor;
import io.github.rifa456.msed.proto.v1.MiningState;
import io.github.rifa456.msed.proto.v1.Vec3i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The mining part of the player state: whether the player is breaking a block and how far the
 * break has got, and for the block under the crosshair its break progress per tick, whether the
 * player can harvest it, and its position relative to the block at the player's feet.
 */
final class Mining {
    private Mining() {}

    /**
     * Reads the mining state from the current game state, on the client thread.
     *
     * @param client the client whose state is recorded
     * @param player the player who mines
     * @return the mining state; the target fields stay unset when the crosshair is not on a block
     */
    static MiningState of(Minecraft client, LocalPlayer player) {
        MultiPlayerGameMode gameMode = client.gameMode;
        MiningState.Builder state = MiningState.newBuilder();
        if (gameMode != null && gameMode.isDestroying()) {
            state.setIsMining(true).setProgress(((MultiPlayerGameModeAccessor) gameMode).getCurrentBreakingProgress());
        }
        ClientLevel level = client.level;
        if (level != null && client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos target = hit.getBlockPos();
            BlockState block = level.getBlockState(target);
            if (!block.isAir()) {
                BlockPos feet = player.blockPosition();
                state.setSpeed(block.getDestroyProgress(player, level, target))
                        .setCanHarvest(player.hasCorrectToolForDrops(block))
                        .setRelativeTargetPos(Vec3i.newBuilder()
                                .setX(target.getX() - feet.getX())
                                .setY(target.getY() - feet.getY())
                                .setZ(target.getZ() - feet.getZ()));
            }
        }
        return state.build();
    }
}
