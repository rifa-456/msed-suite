package io.github.rifa456.msed.mixin.action;

import io.github.rifa456.msed.action.InputPolicy;
import io.github.rifa456.msed.action.InputRecorder;
import io.github.rifa456.msed.proto.v1.CharInput;
import io.github.rifa456.msed.proto.v1.InputEvent;
import io.github.rifa456.msed.proto.v1.KeyInput;
import io.github.rifa456.msed.recording.Recorder;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records every key press and typed character while recording, and blocks the keys that have no
 * gameplay effect.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * Runs before Minecraft reads the key, so a blocked key can be cancelled. The record key is
     * never recorded.
     */
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void msed$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle()) return;
        if (Recorder.getInstance().isRecordKey(event)) {
            // Key mappings do not fire while a screen is open, for example the death screen.
            // The record key is handled here in that case; in the world its mapping handles it.
            if (minecraft.screen != null && action == GLFW.GLFW_PRESS) {
                Recorder.getInstance().pressRecordKey();
            }
            return;
        }
        if (!Recorder.getInstance().isRecording()) return;
        if (InputPolicy.blocksKey(minecraft, event)) {
            ci.cancel();
            return;
        }
        InputRecorder.record(InputEvent.newBuilder()
                .setKey(KeyInput.newBuilder()
                        .setKey(event.key())
                        .setScancode(event.scancode())
                        .setActionValue(action)
                        .setModifiers(event.modifiers()))
                .build());
    }

    /** Records the character only while a screen is open, since only text fields use characters. */
    @Inject(method = "charTyped", at = @At("HEAD"))
    private void msed$onCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle() || !Recorder.getInstance().isRecording()) return;
        if (minecraft.screen == null) return;
        InputRecorder.record(InputEvent.newBuilder()
                .setCharacter(
                        CharInput.newBuilder().setCodepoint(event.codepoint()).setModifiers(event.modifiers()))
                .build());
    }
}
