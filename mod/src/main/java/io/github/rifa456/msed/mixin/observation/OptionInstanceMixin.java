package io.github.rifa456.msed.mixin.observation;

import io.github.rifa456.msed.recording.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disables the fullscreen button in the video settings while recording. */
@Mixin(OptionInstance.class)
public class OptionInstanceMixin {

    @Inject(
            method =
                    "createButton(Lnet/minecraft/client/Options;III)Lnet/minecraft/client/gui/components/AbstractWidget;",
            at = @At("RETURN"))
    private void onWidgetCreated(Options options, int x, int y, int width, CallbackInfoReturnable<AbstractWidget> cir) {
        OptionInstance<?> thisOption = (OptionInstance<?>) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (Recorder.getInstance().isRecording() && thisOption == client.options.fullscreen()) {
            AbstractWidget widget = cir.getReturnValue();
            if (widget != null) {
                widget.active = false;
                widget.setMessage(Component.translatable("options.fullscreen").append(" (Locked by MSED)"));
            }
        }
    }
}
