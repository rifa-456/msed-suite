package io.github.rifa456.msed.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HashIdsTest {
    // Reference values from an independent Murmur3 x64 128-bit implementation (Python's mmh3).
    @Test
    void matchesMurmur3TruncatedToThirtyTwoBits() {
        assertEquals(181_250_759, HashIds.of("minecraft:chest_slot"));
        assertEquals(-1_873_678_030, HashIds.of("event.item_picked_up"));
        assertEquals(1_351_144_950, HashIds.of("net.minecraft.client.gui.screens.inventory.InventoryScreen"));
    }
}
