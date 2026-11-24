package io.github.rifa456.msed.mixin.events;

import io.github.rifa456.msed.proto.v1.BlockBreakEvent;
import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.ItemDroppedEvent;
import io.github.rifa456.msed.proto.v1.Vec3i;
import io.github.rifa456.msed.recording.Recorder;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records the blocks the player breaks and the items the player drops from a container screen. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Unique private @Nullable BlockState msed$brokenState;

    /** Keeps the block state before the block is replaced. */
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void msed$beforeDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        msed$brokenState = client.level != null ? client.level.getBlockState(pos) : null;
    }

    /** Logs the break only if {@code destroyBlock} returned {@code true}. */
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void msed$afterDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState broken = msed$brokenState;
        msed$brokenState = null;
        if (!cir.getReturnValueZ() || broken == null || !Recorder.getInstance().isRecording()) return;
        Recorder.getInstance()
                .logEvent(Event.newBuilder()
                        .setBlockBreak(BlockBreakEvent.newBuilder()
                                .setBlockId(Vocabulary.getInstance().getId(broken))
                                .setPos(Vec3i.newBuilder()
                                        .setX(pos.getX())
                                        .setY(pos.getY())
                                        .setZ(pos.getZ()))));
    }

    /**
     * Logs a drop made with Q on a slot ({@code THROW}) or with a click outside the window while an
     * item is on the cursor.
     */
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void msed$onInventoryClick(
            int containerId, int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!Recorder.getInstance().isRecording()) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId) return;
        ItemStack dropped = ItemStack.EMPTY;
        int count = 0;
        if (clickType == ClickType.THROW
                && slotId >= 0
                && slotId < menu.slots.size()
                && menu.getCarried().isEmpty()) {
            dropped = menu.getSlot(slotId).getItem();
            count = button == 1 ? dropped.getCount() : 1;
        } else if (clickType == ClickType.PICKUP && slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
            dropped = menu.getCarried();
            count = button == 0 ? dropped.getCount() : 1;
        }
        if (dropped.isEmpty() || count <= 0) return;
        Recorder.getInstance()
                .logEvent(Event.newBuilder()
                        .setItemDropped(ItemDroppedEvent.newBuilder()
                                .setItemId(Vocabulary.getInstance().getId(dropped))
                                .setCount(Math.min(count, dropped.getCount()))));
    }
}
