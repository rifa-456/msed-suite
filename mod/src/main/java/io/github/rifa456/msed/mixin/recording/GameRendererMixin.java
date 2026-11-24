package io.github.rifa456.msed.mixin.recording;

import io.github.rifa456.msed.recording.Recorder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Triggers the two vision captures of each frame: before the hand and after the HUD. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /** Runs before the hand is drawn, so this capture holds the world alone. */
    @Inject(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(FZLorg/joml/Matrix4f;)V"))
    private void onPreHandRender(DeltaTracker tickCounter, CallbackInfo ci) {
        Recorder.getInstance().onWorldRendered();
    }

    /** Runs once the frame is complete, HUD and screens included. */
    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderTail(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        Recorder.getInstance().onFrameRendered();
    }
}
