package io.github.rifa456.msed.mixin.observation;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes a container screen's position and hovered slot to the GUI collector, which records slot
 * positions in screen pixels.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int getX();

    @Accessor("topPos")
    int getY();

    @Invoker("getHoveredSlot")
    @Nullable Slot invokeGetSlotAt(double x, double y);
}
