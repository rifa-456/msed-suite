package io.github.rifa456.msed.debug;

import io.github.rifa456.msed.Msed;
import io.github.rifa456.msed.action.InputPolicy;
import io.github.rifa456.msed.recording.Recorder;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Debug hotkeys that toggle the overlays of what the recorder captures (F6, F8, F9) and the minimal
 * render (F10). They work only while not recording, and the input policy blocks them during a
 * recording.
 */
public final class DebugKeys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Msed.MOD_ID, "debug"));

    private static volatile boolean minimalRender;

    private final Toggle nearbyEntities = new Toggle("key.msed.toggle_entity_debug", GLFW.GLFW_KEY_F6);
    private final Toggle visibleBlocks = new Toggle("key.msed.toggle_debug", GLFW.GLFW_KEY_F8);
    private final Toggle rayMatrix = new Toggle("key.msed.toggle_ray_debug", GLFW.GLFW_KEY_F9);
    private final Toggle minimal = new Toggle("key.msed.toggle_minimal_debug", GLFW.GLFW_KEY_F10);

    /** Registers the four hotkeys and adds them to the keys blocked while recording. */
    public DebugKeys() {
        InputPolicy.blockWhileRecording(nearbyEntities.key, visibleBlocks.key, rayMatrix.key, minimal.key);
    }

    /**
     * Returns whether the minimal render is on. Any thread may call this.
     *
     * <p>It is always off while recording, since the recorded frames would be black.
     *
     * @return {@code true} if F10 turned the minimal render on and no recording is running
     */
    public static boolean minimalRender() {
        return minimalRender && !Recorder.getInstance().isRecording();
    }

    /**
     * Returns whether the nearby entities overlay is on.
     *
     * @return {@code true} if F6 turned the overlay on
     */
    public boolean nearbyEntities() {
        return nearbyEntities.on;
    }

    /**
     * Returns whether the visible blocks overlay is on.
     *
     * @return {@code true} if F8 turned the overlay on
     */
    public boolean visibleBlocks() {
        return visibleBlocks.on;
    }

    /**
     * Returns whether the ray matrix overlay is on.
     *
     * @return {@code true} if F9 turned the overlay on
     */
    public boolean rayMatrix() {
        return rayMatrix.on;
    }

    /**
     * Applies the hotkey presses since the previous tick, on the client thread, once per tick.
     * Presses are ignored while recording.
     *
     * @param client the client whose key mappings are read
     */
    public void onClientTick(Minecraft client) {
        if (Recorder.getInstance().isRecording()) {
            return;
        }
        nearbyEntities.poll(client);
        visibleBlocks.poll(client);
        rayMatrix.poll(client);
        minimal.poll(client);
        minimalRender = minimal.on;
    }

    /** A hotkey and the on or off state it switches, announced above the hotbar. */
    private static final class Toggle {
        private final KeyMapping key;
        private boolean on;

        Toggle(String name, int defaultKey) {
            key = KeyBindingHelper.registerKeyBinding(new KeyMapping(name, defaultKey, CATEGORY));
        }

        void poll(Minecraft client) {
            while (key.consumeClick()) {
                on = !on;
                if (client.player != null) {
                    Component state = on
                            ? Component.translatable("options.on").withStyle(ChatFormatting.GREEN)
                            : Component.translatable("options.off").withStyle(ChatFormatting.RED);
                    client.player.displayClientMessage(
                            Component.translatable(
                                    "text.msed.hud.key_toggle_message",
                                    Component.translatable(key.getName()),
                                    key.getTranslatedKeyMessage(),
                                    state),
                            true);
                }
            }
        }
    }
}
