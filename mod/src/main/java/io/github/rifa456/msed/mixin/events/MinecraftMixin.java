package io.github.rifa456.msed.mixin.events;

import io.github.rifa456.msed.events.ClientDamageSource;
import io.github.rifa456.msed.observation.ContainerBlock;
import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.GuiClosedEvent;
import io.github.rifa456.msed.proto.v1.GuiOpenedEvent;
import io.github.rifa456.msed.proto.v1.PlayerDeathEvent;
import io.github.rifa456.msed.recording.Recorder;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records GUI open and close events and the player's death, and notes the block the player uses so
 * a container screen can be tied to it.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow
    public @Nullable Screen screen;

    @Shadow
    public @Nullable HitResult hitResult;

    @Shadow
    public @Nullable ClientLevel level;

    /** Logs the close of the current screen, before the new screen replaces it. */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onPreSetScreen(Screen screen, CallbackInfo ci) {
        if (this.screen != null && Recorder.getInstance().isRecording()) {
            int screenId = Vocabulary.getInstance().getId(this.screen);
            Recorder.getInstance()
                    .logEvent(Event.newBuilder()
                            .setGuiClosed(GuiClosedEvent.newBuilder()
                                    .setScreenId(screenId)
                                    .build()));
            ContainerBlock.onScreenClosed();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void onDoItemUse(CallbackInfo ci) {
        if (this.hitResult instanceof BlockHitResult blockHitResult) {
            if (this.level != null) {
                BlockPos pos = blockHitResult.getBlockPos();
                Block block = this.level.getBlockState(pos).getBlock();
                ContainerBlock.onBlockUsed(BuiltInRegistries.BLOCK.getKey(block));
            }
        }
    }

    /**
     * Logs the open of the new screen once it is set. A death screen also logs the death, with the
     * last damage source the client received.
     */
    @Inject(method = "setScreen", at = @At("TAIL"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen != null && Recorder.getInstance().isRecording()) {
            int screenId = Vocabulary.getInstance().getId(screen);
            ContainerBlock.onScreenOpened();
            Recorder.getInstance()
                    .logEvent(Event.newBuilder()
                            .setGuiOpened(GuiOpenedEvent.newBuilder()
                                    .setScreenId(screenId)
                                    .build()));
        }
        if (screen instanceof DeathScreen) {
            if (Recorder.getInstance().isRecording()) {
                ClientDamageSource lastSource = Recorder.getInstance().getLastDamageSource();
                Vocabulary vocab = Vocabulary.getInstance();
                int typeId = lastSource != null ? vocab.getId(lastSource.type()) : Vocabulary.NONE;
                int entityId = lastSource != null ? vocab.entityTypeId(lastSource.entityId()) : Vocabulary.NONE;
                Recorder.getInstance().setLastDamageSource(null);
                Recorder.getInstance()
                        .logEvent(Event.newBuilder()
                                .setDeath(PlayerDeathEvent.newBuilder()
                                        .setSourceTypeId(typeId)
                                        .setSourceEntityId(entityId)
                                        .build()));
            }
        }
    }
}
