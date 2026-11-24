package io.github.rifa456.msed.recording;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.rifa456.msed.Msed;
import io.github.rifa456.msed.action.InputPolicy;
import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.config.MsedConfigScreen;
import io.github.rifa456.msed.events.ClientDamageSource;
import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.RecordingStoppedEvent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the record key into recording sessions. Two presses of the key within 500 ms start a
 * session, after the countdown set in the config, and two more stop it. At most one session runs
 * at a time.
 *
 * <p>Every method runs on the client thread, which in Minecraft also renders.
 */
public final class Recorder {
    private static final Logger LOG = LoggerFactory.getLogger(Recorder.class);
    private static final Recorder INSTANCE = new Recorder();
    /** Longest gap, in milliseconds, between the two presses that start or stop recording. */
    private static final long DOUBLE_TAP_MS = 500;

    private @Nullable KeyMapping recordKey;
    private @Nullable KeyMapping settingsKey;
    private @Nullable Session currentSession;
    private @Nullable ClientDamageSource lastDamageSource;
    private int countdownTicks;
    private long firstTapAt;
    private boolean awaitingSecondTap;

    private Recorder() {}

    /**
     * Returns the recorder of this client.
     *
     * @return the only recorder
     */
    public static Recorder getInstance() {
        return INSTANCE;
    }

    /**
     * Registers MSED's keys in the controls screen: the record key, F12 by default, and the key
     * that opens the settings, F7 by default. Call once, at client start.
     */
    public void registerKeys() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Msed.MOD_ID, "main"));
        // Not F4: vanilla binds it, and a key press reaches only one mapping.
        recordKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.msed.toggle_recording", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F12, category));
        KeyMapping settings = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.msed.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));
        settingsKey = settings;
        InputPolicy.blockWhileRecording(settings);
    }

    /**
     * Tells whether a key event is the record key. The keyboard hook uses this to keep the record
     * key out of the recorded actions, and to catch it while a screen is open.
     *
     * @param event the key event to test
     * @return {@code true} if the event matches the record key; {@code false} before
     *     {@link #registerKeys} has run
     */
    public boolean isRecordKey(KeyEvent event) {
        KeyMapping key = recordKey;
        return key != null && key.matches(event);
    }

    /**
     * Stores the source of the latest damage the player took, for the death event that may follow.
     *
     * @param source the damage source, or {@code null} to clear it
     */
    public void setLastDamageSource(@Nullable ClientDamageSource source) {
        this.lastDamageSource = source;
    }

    /**
     * Returns the source of the latest damage the player took in this session.
     *
     * @return the damage source, or {@code null} if the player took no damage since the session
     *     started
     */
    public @Nullable ClientDamageSource getLastDamageSource() {
        return lastDamageSource;
    }

    /**
     * Tells whether a session is recording now. The countdown before a session does not count.
     *
     * @return {@code true} while a session is active
     */
    public boolean isRecording() {
        Session session = currentSession;
        return session != null && session.isActive();
    }

    /**
     * Queues an event for the next tick record of the active session. Without an active session
     * the event is dropped.
     *
     * @param event the event, without a tick number
     */
    public void logEvent(Event.Builder event) {
        Session session = currentSession;
        if (session != null && session.isActive()) {
            session.logEvent(event);
        }
    }

    /** Stops the recording, or cancels its countdown, when the player leaves the world. */
    public void shutdown() {
        awaitingSecondTap = false;
        stop();
    }

    /**
     * Advances the recorder by one client tick: reads record key presses, opens the settings when
     * their key is pressed outside a recording, counts down to the start and collects one tick of
     * the active session. A paused game records no tick.
     *
     * @param client the client whose state is recorded
     */
    public void onClientTick(Minecraft client) {
        KeyMapping key = recordKey;
        if (key != null) {
            while (key.consumeClick()) {
                pressRecordKey();
            }
        }
        KeyMapping settings = settingsKey;
        if (settings != null) {
            while (settings.consumeClick()) {
                if (currentSession == null && countdownTicks == 0) {
                    client.setScreen(MsedConfigScreen.create(client.screen));
                }
            }
        }
        if (countdownTicks > 0) {
            countdownTicks--;
            if (countdownTicks == 0) {
                start(client);
            }
            return;
        }
        Session session = currentSession;
        if (session != null && session.isActive() && !client.isPaused()) {
            session.tick(client);
        }
    }

    /**
     * Captures the world-only frame if the latest tick still needs one. The render hook calls this
     * once the world is drawn, before the hand and HUD.
     */
    public void onWorldRendered() {
        Session session = currentSession;
        if (session != null && session.isActive() && session.tryConsumeWorldCapture()) {
            session.getCollector().getVisionCollector().capturePureWorld(Minecraft.getInstance());
        }
    }

    /**
     * Captures the composited frame if the latest tick still needs one. The render hook calls this
     * once the frame is complete, HUD included, before the buffer swap.
     */
    public void onFrameRendered() {
        Session session = currentSession;
        if (session != null && session.isActive() && session.tryConsumeGuiCapture()) {
            session.getCollector().getVisionCollector().captureGuiWorld(Minecraft.getInstance());
        }
    }

    /**
     * Draws the double-press hint and the countdown on the HUD, while either applies.
     *
     * @param graphics the HUD's graphics context
     */
    public void onHudRender(GuiGraphics graphics) {
        boolean hint = awaitingSecondTap && System.currentTimeMillis() - firstTapAt <= DOUBLE_TAP_MS;
        StatusMessages.render(graphics, countdownTicks, hint);
    }

    /**
     * Counts one press of the record key. A second press within 500 ms starts recording, stops
     * it, or cancels the countdown. The keyboard hook also calls this while a screen is open,
     * because key mappings do not fire there.
     */
    public void pressRecordKey() {
        long now = System.currentTimeMillis();
        if (awaitingSecondTap && now - firstTapAt <= DOUBLE_TAP_MS) {
            awaitingSecondTap = false;
            toggle();
        } else {
            awaitingSecondTap = true;
            firstTapAt = now;
        }
    }

    private void toggle() {
        if (isRecording() || countdownTicks > 0) {
            logEvent(Event.newBuilder().setStopped(RecordingStoppedEvent.getDefaultInstance()));
            stop();
            return;
        }
        if (!inWorld(Minecraft.getInstance())) {
            LOG.info("The record key does nothing outside a world");
            return;
        }
        int seconds = ConfigFile.getConfig().getRecordingBehavior().getGracePeriodSeconds();
        if (seconds <= 0) {
            start(Minecraft.getInstance());
        } else {
            countdownTicks = seconds * SharedConstants.TICKS_PER_SECOND;
            StatusMessages.send(Component.translatable("text.msed.hud.countdown", seconds));
        }
    }

    private void start(Minecraft client) {
        if (currentSession != null || !inWorld(client)) {
            return;
        }
        Session session;
        try {
            session = new Session(UserId.getUserId());
        } catch (RuntimeException e) {
            LOG.error("Could not create the episode; recording did not start", e);
            StatusMessages.send(Component.translatable("text.msed.hud.start_failed"));
            return;
        }
        currentSession = session;
        try {
            session.start(client);
        } catch (RuntimeException e) {
            LOG.error("Could not start recording", e);
            session.abort();
            currentSession = null;
            StatusMessages.send(Component.translatable("text.msed.hud.start_failed"));
            return;
        }
        StatusMessages.send(Component.translatable("text.msed.hud.recording_started"));
    }

    private static boolean inWorld(Minecraft client) {
        return client.player != null && client.level != null;
    }

    private void stop() {
        countdownTicks = 0;
        Session session = currentSession;
        if (session != null) {
            StatusMessages.send(Component.translatable("text.msed.hud.recording_stopped"));
            session.stop();
            currentSession = null;
        }
    }
}
