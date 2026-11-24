package io.github.rifa456.msed.mixin.observation;

import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes a container menu's data slots, such as furnace progress, to the GUI collector. */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Accessor("dataSlots")
    List<DataSlot> getProperties();
}
