package io.github.rifa456.msed.observation;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The block whose container screen is open. The GUI modality uses it to tag the plain slots of a
 * block's container, for example {@code chest_slot}. The screen and item-use hooks update it on
 * the client thread; any thread may read it.
 */
public final class ContainerBlock {
    /** The longest time between using a block and a screen opening for the screen to belong to the block. */
    private static final long USE_TO_SCREEN_MS = 1000;

    private static volatile @Nullable Identifier used;
    private static volatile long usedAt;
    private static volatile @Nullable Identifier open;

    private ContainerBlock() {}

    /**
     * Remembers a block the player used, as the candidate owner of the next screen that opens.
     *
     * @param block the registry id of the used block
     */
    public static void onBlockUsed(Identifier block) {
        used = block;
        usedAt = System.currentTimeMillis();
    }

    /**
     * Makes the remembered block the owner of the screen that just opened, if the player used it
     * less than a second ago. Forgets the remembered block either way.
     */
    public static void onScreenOpened() {
        // A stale use, for example placing a block earlier, must not tag an unrelated container.
        open = System.currentTimeMillis() - usedAt < USE_TO_SCREEN_MS ? used : null;
        used = null;
    }

    /** Forgets the owner of the screen that just closed. */
    public static void onScreenClosed() {
        open = null;
    }

    /**
     * Returns the block whose container screen is open.
     *
     * @return the block's registry id, or {@code null} when no screen is open or the open screen
     *     did not follow a block use
     */
    public static @Nullable Identifier open() {
        return open;
    }
}
