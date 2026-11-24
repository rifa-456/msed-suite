package io.github.rifa456.msed.action;

import io.github.rifa456.msed.observation.AbstractCollector;
import io.github.rifa456.msed.proto.v1.ActionState;
import io.github.rifa456.msed.proto.v1.KeyboardState;
import io.github.rifa456.msed.proto.v1.MouseState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The player's action in one tick. It holds a summary, that is the camera movement and the
 * gameplay bindings held at the end of the tick, and every raw input the game received since the
 * previous tick.
 */
public final class ActionCollector extends AbstractCollector<ActionState> {

    @Override
    public String getProfilerName() {
        return "msed:action";
    }

    @Override
    protected ActionState collect(Minecraft client, ProfilerFiller profiler) {
        MouseState mouse = collectMouse(client);
        KeyboardState keyboard = collectKeyboard(client);
        return ActionState.newBuilder()
                .setMouse(mouse)
                .setKeyboard(keyboard)
                .addAllInputs(InputRecorder.drainInputs())
                .build();
    }

    private MouseState collectMouse(Minecraft client) {
        double[] movement = InputRecorder.drainMouseMovement();
        float dx = (float) movement[0];
        float dy = (float) movement[1];
        boolean attack = client.options.keyAttack.isDown();
        boolean use = client.options.keyUse.isDown();
        return MouseState.newBuilder()
                .setDx(dx)
                .setDy(dy)
                .setAttack(attack)
                .setUse(use)
                .build();
    }

    private KeyboardState collectKeyboard(Minecraft client) {
        KeyboardState.Builder kb = KeyboardState.newBuilder()
                .setForward(client.options.keyUp.isDown())
                .setBack(client.options.keyDown.isDown())
                .setLeft(client.options.keyLeft.isDown())
                .setRight(client.options.keyRight.isDown())
                .setJump(client.options.keyJump.isDown())
                .setSneak(client.options.keyShift.isDown())
                .setSprint(client.options.keySprint.isDown())
                .setDrop(client.options.keyDrop.isDown())
                .setInventory(client.options.keyInventory.isDown())
                .setSwapHands(client.options.keySwapOffhand.isDown());

        KeyMapping[] hotbarKeys = client.options.keyHotbarSlots;
        for (int i = 0; i < 9; i++) {
            boolean pressed = hotbarKeys[i] != null && hotbarKeys[i].isDown();
            kb.addHotbarKeys(pressed);
        }

        return kb.build();
    }
}
