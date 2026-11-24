package io.github.rifa456.msed.mixin.events;

import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.ItemDroppedEvent;
import io.github.rifa456.msed.proto.v1.PlayerRespawnEvent;
import io.github.rifa456.msed.proto.v1.PlayerSleepEvent;
import io.github.rifa456.msed.proto.v1.Vec3i;
import io.github.rifa456.msed.recording.Recorder;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records items the player drops from the hotbar and the respawn and sleep events. Disables
 * double-tap-W sprint while recording, since that sprint has no input of its own.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Shadow
    protected int sprintTriggerTime;

    @Unique private boolean hasLoggedSuccessfulSleep = false;

    @Inject(method = "drop(Z)Z", at = @At("HEAD"))
    private void onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        ItemStack stackToDrop = player.getMainHandItem();
        if (stackToDrop.isEmpty()) {
            return;
        }
        int count = entireStack ? stackToDrop.getCount() : 1;
        int itemId = Vocabulary.getInstance().getId(stackToDrop);
        Recorder.getInstance()
                .logEvent(Event.newBuilder()
                        .setItemDropped(ItemDroppedEvent.newBuilder()
                                .setItemId(itemId)
                                .setCount(count)
                                .build()));
    }

    /** Resets the double-tap timer each tick, so a second W press never starts a sprint. */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onTickMovement(CallbackInfo ci) {
        if (Recorder.getInstance().isRecording()) {
            this.sprintTriggerTime = 0;
        }
    }

    /** Logs the respawn when the client requests it, not when the server completes it. */
    @Inject(method = "respawn", at = @At("HEAD"))
    private void onRequestRespawn(CallbackInfo ci) {
        Recorder.getInstance().logEvent(Event.newBuilder().setRespawn(PlayerRespawnEvent.getDefaultInstance()));
    }

    /**
     * Logs one sleep event per time in bed, once the sleep timer reaches 100 ticks, the point
     * where the player counts as fully asleep.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!player.isSleeping()) {
            this.hasLoggedSuccessfulSleep = false;
            return;
        }
        int currentSleepTimer = player.getSleepTimer();
        if (currentSleepTimer >= 100 && !this.hasLoggedSuccessfulSleep) {
            player.getSleepingPos().ifPresent(pos -> {
                Vec3i bedPos = Vec3i.newBuilder()
                        .setX(pos.getX())
                        .setY(pos.getY())
                        .setZ(pos.getZ())
                        .build();
                Recorder.getInstance()
                        .logEvent(Event.newBuilder()
                                .setSleep(PlayerSleepEvent.newBuilder()
                                        .setBedPos(bedPos)
                                        .build()));
            });
            this.hasLoggedSuccessfulSleep = true;
        }
    }
}
