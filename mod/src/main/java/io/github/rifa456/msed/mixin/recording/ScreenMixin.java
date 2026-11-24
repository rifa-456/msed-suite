package io.github.rifa456.msed.mixin.recording;

import io.github.rifa456.msed.recording.Recorder;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops Esc from closing screens while recording. Closing a screen with Esc is not one of the
 * recorded gameplay actions; the inventory key closes screens instead, and it is recorded.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
    private void modifyShouldCloseOnEsc(CallbackInfoReturnable<Boolean> cir) {
        if (Recorder.getInstance().isRecording()) {
            cir.setReturnValue(false);
        }
    }
}
