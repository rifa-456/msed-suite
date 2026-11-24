package io.github.rifa456.msed.observation;

import static net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;
import static net.minecraft.world.entity.player.Inventory.getSelectionSize;

import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.proto.v1.InventoryState;
import io.github.rifa456.msed.proto.v1.ItemStack;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The inventory modality: the selected hotbar slot and, as the configuration selects, the stacks
 * of the hotbar, the main inventory, the armor slots from feet to head, and the offhand. An empty
 * slot is recorded as an empty stack, so each list keeps the positions of its slots.
 */
public final class InventoryCollector extends AbstractCollector<InventoryState> {

    @Override
    public String getProfilerName() {
        return "msed:inventory";
    }

    @Override
    public InventoryState collect(Minecraft client, ProfilerFiller profiler) {
        LocalPlayer player = client.player;
        if (player == null) {
            return InventoryState.getDefaultInstance();
        }
        MsedConfig config = ConfigFile.getConfig();
        Vocabulary vocab = Vocabulary.getInstance();
        InventoryState.Builder builder = InventoryState.newBuilder();
        builder.setSelectedSlot(player.getInventory().getSelectedSlot());
        if (config.getRecording().getRecordInventory().getRecordHotbar()) {
            List<net.minecraft.world.item.ItemStack> items =
                    player.getInventory().getNonEquipmentItems();
            int hotbarSize = Math.min(items.size(), getSelectionSize());
            List<net.minecraft.world.item.ItemStack> hotbarStacks = items.subList(0, hotbarSize);
            for (net.minecraft.world.item.ItemStack mcStack : hotbarStacks) {
                builder.addHotbar(convertItemStack(mcStack, vocab));
            }
        }
        if (config.getRecording().getRecordInventory().getRecordMain()) {
            List<net.minecraft.world.item.ItemStack> items =
                    player.getInventory().getNonEquipmentItems();
            if (items.size() >= INVENTORY_SIZE) {
                List<net.minecraft.world.item.ItemStack> mainStacks = items.subList(getSelectionSize(), INVENTORY_SIZE);
                for (net.minecraft.world.item.ItemStack mcStack : mainStacks) {
                    builder.addMain(convertItemStack(mcStack, vocab));
                }
            }
        }
        if (config.getRecording().getRecordInventory().getRecordArmor()) {
            net.minecraft.world.entity.EquipmentSlot[] armorSlots = {
                net.minecraft.world.entity.EquipmentSlot.FEET,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.HEAD
            };
            for (net.minecraft.world.entity.EquipmentSlot slot : armorSlots) {
                builder.addArmor(convertItemStack(player.getItemBySlot(slot), vocab));
            }
        }
        if (config.getRecording().getRecordInventory().getRecordOffhand()) {
            net.minecraft.world.item.ItemStack offhandStack = player.getOffhandItem();
            builder.addOffhand(convertItemStack(offhandStack, vocab));
        }
        return builder.build();
    }

    /** Returns a stack as the schema records it; a null or empty stack becomes the empty stack. */
    private ItemStack convertItemStack(net.minecraft.world.item.ItemStack mcStack, Vocabulary vocab) {
        if (mcStack == null || mcStack.isEmpty()) {
            return ItemStack.getDefaultInstance();
        }
        return ItemStack.newBuilder()
                .setItemId(vocab.getId(mcStack.getItem()))
                .setCount(mcStack.getCount())
                .setDamage(mcStack.getDamageValue())
                .build();
    }
}
