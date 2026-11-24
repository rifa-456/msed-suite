package io.github.rifa456.msed.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The set of inputs that never reach the game while recording. It holds the inputs with no
 * gameplay effect: chat, commands, screenshots, camera perspective, HUD toggles and debug keys.
 * Blocking them means every change of state in a recording comes from an input that is recorded.
 */
public final class InputPolicy {
    private static final List<KeyMapping> OWN_KEYS = new ArrayList<>();
    private static @Nullable List<KeyMapping> blocked;

    private InputPolicy() {}

    private static List<KeyMapping> blocked() {
        List<KeyMapping> result = blocked;
        if (result == null) {
            Options o = Minecraft.getInstance().options;
            List<KeyMapping> list = new ArrayList<>(List.of(
                    o.keyChat,
                    o.keyCommand,
                    o.keyPlayerList,
                    o.keySocialInteractions,
                    o.keyScreenshot,
                    o.keyTogglePerspective,
                    o.keySmoothCamera,
                    o.keyFullscreen,
                    o.keyAdvancements,
                    o.keyQuickActions,
                    o.keyToggleGui,
                    o.keyToggleSpectatorShaderEffects,
                    o.keySaveHotbarActivator,
                    o.keyLoadHotbarActivator,
                    o.keySpectatorOutlines,
                    o.keySpectatorHotbar,
                    o.keyPickItem,
                    // F3. The other debug mappings are F3 combinations bound to ordinary keys, such as
                    // A, D or 1. Blocking F3 alone makes every combination impossible.
                    o.keyDebugOverlay,
                    o.keyDebugModifier));
            list.addAll(OWN_KEYS);
            result = List.copyOf(list);
            blocked = result;
        }
        return result;
    }

    /**
     * Adds MSED's own non-gameplay keys to the blocked set, next to the game's. Call this on the
     * client thread.
     *
     * @param mappings the key mappings to block while recording
     */
    public static void blockWhileRecording(KeyMapping... mappings) {
        OWN_KEYS.addAll(List.of(mappings));
        blocked = null;
    }

    /**
     * Returns whether a key event must be dropped before it reaches the game.
     *
     * <p>While a screen is open, only keys outside the text range are blocked. A letter bound to
     * chat, for example, still reaches a sign or an anvil.
     *
     * @param client the client receiving the event
     * @param event the key event
     * @return {@code true} if the key matches a blocked mapping and must be dropped
     */
    public static boolean blocksKey(Minecraft client, KeyEvent event) {
        if (client.screen != null && event.key() < GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        for (KeyMapping mapping : blocked()) {
            if (mapping.matches(event)) return true;
        }
        return false;
    }

    /**
     * Returns whether a mouse button event must be dropped before it reaches the game, for example
     * pick block. Mouse buttons are never blocked while a screen is open.
     *
     * @param client the client receiving the event
     * @param button the GLFW mouse button code
     * @return {@code true} if the button is bound to a blocked mapping and no screen is open
     */
    public static boolean blocksMouseButton(Minecraft client, int button) {
        if (client.screen != null) return false;
        for (KeyMapping mapping : blocked()) {
            InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(mapping);
            if (bound.getType() == InputConstants.Type.MOUSE && bound.getValue() == button) return true;
        }
        return false;
    }
}
