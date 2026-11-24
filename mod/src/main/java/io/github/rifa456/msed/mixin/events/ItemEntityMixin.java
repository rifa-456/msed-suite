package io.github.rifa456.msed.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.rifa456.msed.events.ItemPickedUpS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends the client the items the player picks up. It runs on the integrated server, so pickups are
 * recorded only in single player.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(
            method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
    private void onItemPickup(
            Player player, CallbackInfo ci, @Local ItemStack stack, @Local Item item, @Local int countBefore) {
        // Inventory.add has already run, so what is left in the stack did not fit.
        int taken = countBefore - stack.getCount();
        if (taken > 0 && player instanceof ServerPlayer serverPlayer) {
            ItemPickedUpS2CPayload payload = new ItemPickedUpS2CPayload(BuiltInRegistries.ITEM.getKey(item), taken);
            ServerPlayNetworking.send(serverPlayer, payload);
        }
    }
}
