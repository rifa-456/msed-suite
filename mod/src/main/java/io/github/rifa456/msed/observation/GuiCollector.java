package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.mixin.observation.AbstractContainerMenuAccessor;
import io.github.rifa456.msed.mixin.observation.AbstractContainerScreenAccessor;
import io.github.rifa456.msed.proto.v1.GuiSlot;
import io.github.rifa456.msed.proto.v1.GuiState;
import io.github.rifa456.msed.proto.v1.ItemStack;
import io.github.rifa456.msed.proto.v1.ObservationKey;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CrafterSlot;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.FurnaceFuelSlot;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.NonInteractiveResultSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;

/**
 * The GUI modality: the open screen and the mouse position, and for a container screen also the
 * carried item, the hovered slot, every slot with its item and tag, and the menu's data values.
 * Positions are in GUI-scaled pixels.
 *
 * <p>A tick with no screen open, or with GUI recording off, records an empty state. The largest
 * slot and data-value counts of the session go into the episode metadata.
 */
public final class GuiCollector extends TrackingCollector<GuiState> {
    /** Tags of the game's public slot classes; each class's subclasses get the same tag. */
    private static final List<Map.Entry<Class<? extends Slot>, String>> SLOT_CLASS_TAGS = List.of(
            Map.entry(FurnaceResultSlot.class, "furnace_result_slot"),
            Map.entry(FurnaceFuelSlot.class, "furnace_fuel_slot"),
            Map.entry(ResultSlot.class, "result_slot"),
            Map.entry(MerchantResultSlot.class, "merchant_result_slot"),
            Map.entry(NonInteractiveResultSlot.class, "non_interactive_result_slot"),
            Map.entry(CrafterSlot.class, "crafter_slot"),
            Map.entry(ShulkerBoxSlot.class, "shulker_box_slot"));

    private final StatTracker slotTracker;
    private final StatTracker propertyTracker;

    @Override
    public String getProfilerName() {
        return "msed:gui";
    }

    /** Creates a collector that measures the slot and data-value counts of each screen. */
    public GuiCollector() {
        this.slotTracker = registerTracker(ObservationKey.OBSERVATION_KEY_GUI_MAX_SLOTS);
        this.propertyTracker = registerTracker(ObservationKey.OBSERVATION_KEY_GUI_MAX_PROPERTIES);
    }

    @Override
    public GuiState collect(Minecraft client, ProfilerFiller profiler) {
        Screen currentScreen = client.screen;
        if (!ConfigFile.getConfig().getRecording().getRecordGui().getRecordGuiState() || currentScreen == null) {
            return GuiState.getDefaultInstance();
        }
        GuiState.Builder stateBuilder = GuiState.newBuilder();
        stateBuilder.setIsOpen(true);
        Vocabulary vocab = Vocabulary.getInstance();
        stateBuilder.setScreenId(vocab.getId(currentScreen));
        double winWidth = client.getWindow().getScreenWidth();
        double winHeight = client.getWindow().getScreenHeight();
        double scaledWidth = client.getWindow().getGuiScaledWidth();
        double scaledHeight = client.getWindow().getGuiScaledHeight();
        int scaledMouseX;
        int scaledMouseY;
        if (winWidth > 0 && winHeight > 0) {
            scaledMouseX = (int) (client.mouseHandler.xpos() * scaledWidth / winWidth);
            scaledMouseY = (int) (client.mouseHandler.ypos() * scaledHeight / winHeight);
            stateBuilder.setMouseX(scaledMouseX);
            stateBuilder.setMouseY(scaledMouseY);
        }
        if (!(currentScreen instanceof AbstractContainerScreen<?> screen)) {
            return stateBuilder.build();
        }
        AbstractContainerMenu handler = screen.getMenu();
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        net.minecraft.world.item.ItemStack cursorStack = handler.getCarried();
        if (!cursorStack.isEmpty()) {
            stateBuilder.setCursorItem(convertItemStack(cursorStack, vocab));
        }
        Slot hoveredSlot = winWidth > 0 && winHeight > 0
                ? accessor.invokeGetSlotAt(
                        client.mouseHandler.xpos() * scaledWidth / winWidth,
                        client.mouseHandler.ypos() * scaledHeight / winHeight)
                : null;
        if (hoveredSlot != null) {
            stateBuilder.setHoveredSlotIndex(hoveredSlot.index);
        }
        int numSlots = handler.slots.size();
        slotTracker.observe(numSlots);
        for (int i = 0; i < numSlots; i++) {
            Slot slot = handler.slots.get(i);
            GuiSlot.Builder slotBuilder = GuiSlot.newBuilder();
            slotBuilder.setIndex(slot.index);
            slotBuilder.setPixelX(accessor.getX() + slot.x);
            slotBuilder.setPixelY(accessor.getY() + slot.y);
            if (slot.hasItem()) {
                slotBuilder.setItem(convertItemStack(slot.getItem(), vocab));
            }
            String tagString = getSemanticTag(slot, screen);
            int tagId = vocab.getId(tagString);
            slotBuilder.setSemanticTagId(tagId);
            stateBuilder.addSlots(slotBuilder.build());
        }
        AbstractContainerMenuAccessor sha = (AbstractContainerMenuAccessor) handler;
        List<DataSlot> properties = sha.getProperties();
        if (properties != null && !properties.isEmpty()) {
            propertyTracker.observe(properties.size());
            for (DataSlot prop : properties) {
                stateBuilder.addProperties(prop.get());
            }
        }
        return stateBuilder.build();
    }

    private ItemStack convertItemStack(net.minecraft.world.item.ItemStack mcStack, Vocabulary vocab) {
        return ItemStack.newBuilder()
                .setItemId(vocab.getId(mcStack.getItem()))
                .setCount(mcStack.getCount())
                .setDamage(mcStack.getDamageValue())
                .build();
    }

    /**
     * Returns the semantic tag of a slot: the role it plays in its screen, as a vocabulary name.
     * A slot of the player's inventory gets a tag such as {@code player_hotbar} or
     * {@code crafting_2x2_input}. A slot of one of the game's dedicated slot classes gets that
     * class's tag, for example {@code furnace_result_slot}. Any other slot of a block's container
     * gets the block name, for example {@code chest_slot}. Class names are never used: a released
     * game runs with obfuscated ones.
     *
     * @param slot the slot to tag
     * @param screen the container screen the slot belongs to
     * @return the tag, or {@code unknown_container_slot} when nothing names the slot
     */
    public String getSemanticTag(Slot slot, AbstractContainerScreen<?> screen) {
        AbstractContainerMenu handler = screen.getMenu();
        if (handler instanceof InventoryMenu) {
            int id = slot.index;
            if (id == 0) return "crafting_2x2_output";
            if (id >= 1 && id <= 4) return "crafting_2x2_input";
            if (id >= 5 && id <= 8) return "player_armor";
            if (id >= 9 && id <= 35) return "player_main";
            if (id >= 36 && id <= 44) return "player_hotbar";
            if (id == 45) return "player_offhand";
        }
        if (slot.container instanceof Inventory) {
            int indexInPlayerInventory = slot.getContainerSlot();
            if (indexInPlayerInventory >= 0 && indexInPlayerInventory <= 8) return "player_hotbar";
            if (indexInPlayerInventory >= 9 && indexInPlayerInventory <= 35) return "player_main";
            if (indexInPlayerInventory >= 36 && indexInPlayerInventory <= 39) return "player_armor";
            if (indexInPlayerInventory == 40) return "player_offhand";
        }
        for (Map.Entry<Class<? extends Slot>, String> entry : SLOT_CLASS_TAGS) {
            if (entry.getKey().isInstance(slot)) return entry.getValue();
        }
        Identifier block = ContainerBlock.open();
        if (block != null) {
            return block.getPath() + "_slot";
        }
        return "unknown_container_slot";
    }
}
