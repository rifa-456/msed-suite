package io.github.rifa456.msed.recording;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Recording status shown to the player: chat lines and the start countdown on the HUD. */
final class StatusMessages {
    private static final int MARGIN = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int HINT_COLOR = 0xFFFFFF55;

    private StatusMessages() {}

    /**
     * Adds a line to the player's chat, after a green {@code [MSED]} prefix. Does nothing when
     * there is no player. Call on the client thread.
     *
     * @param message the text of the line
     */
    static void send(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.gui
                    .getChat()
                    .addMessage(Component.literal("[MSED] ")
                            .withStyle(ChatFormatting.GREEN)
                            .append(message.copy().withStyle(ChatFormatting.WHITE)));
        }
    }

    /**
     * Draws the status lines in the top left corner of the HUD: the double-press hint, then the
     * seconds left in the countdown, rounded up.
     *
     * @param graphics the HUD's graphics context
     * @param countdownTicks the ticks left before recording starts, or 0 when no countdown runs
     * @param awaitingSecondTap whether to show the hint to press the record key again
     */
    static void render(GuiGraphics graphics, int countdownTicks, boolean awaitingSecondTap) {
        Minecraft client = Minecraft.getInstance();
        int y = MARGIN;
        if (awaitingSecondTap) {
            graphics.drawString(
                    client.font, Component.translatable("text.msed.hud.double_tap_rec"), MARGIN, y, HINT_COLOR);
            y += LINE_HEIGHT;
        }
        if (countdownTicks > 0) {
            int seconds = (countdownTicks + SharedConstants.TICKS_PER_SECOND - 1) / SharedConstants.TICKS_PER_SECOND;
            graphics.drawString(
                    client.font, Component.translatable("text.msed.hud.countdown", seconds), MARGIN, y, HINT_COLOR);
        }
    }
}
