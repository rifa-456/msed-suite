package io.github.rifa456.msed.mixin.action;

import io.github.rifa456.msed.action.InputPolicy;
import io.github.rifa456.msed.action.InputRecorder;
import io.github.rifa456.msed.proto.v1.InputEvent;
import io.github.rifa456.msed.proto.v1.MouseButtonInput;
import io.github.rifa456.msed.proto.v1.ScrollInput;
import io.github.rifa456.msed.recording.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records mouse buttons, scrolling and camera movement while recording, and blocks the pick block
 * button.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void msed$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle() || !Recorder.getInstance().isRecording()) return;
        if (InputPolicy.blocksMouseButton(minecraft, info.button())) {
            ci.cancel();
            return;
        }
        InputRecorder.record(InputEvent.newBuilder()
                .setMouseButton(MouseButtonInput.newBuilder()
                        .setButton(info.button())
                        .setActionValue(action)
                        .setModifiers(info.modifiers()))
                .build());
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void msed$onScroll(long window, double dx, double dy, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle() || !Recorder.getInstance().isRecording()) return;
        InputRecorder.record(InputEvent.newBuilder()
                .setScroll(ScrollInput.newBuilder().setDx(dx).setDy(dy))
                .build());
    }

    /** Runs once per frame, before the accumulated mouse movement turns the camera. */
    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void msed$onTurnPlayer(double movementTime, CallbackInfo ci) {
        if (Recorder.getInstance().isRecording()) {
            InputRecorder.addMouseMovement(accumulatedDX, accumulatedDY);
        }
    }
}
