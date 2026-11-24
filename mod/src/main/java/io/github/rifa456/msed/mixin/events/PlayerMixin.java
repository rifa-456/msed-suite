package io.github.rifa456.msed.mixin.events;

import io.github.rifa456.msed.events.PlayerDamagedS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends the client the damage the player takes, from the integrated server. The amount is the
 * health and absorption actually lost, after armor and effects.
 *
 * <p>The hook is on {@code Player} because it overrides {@code LivingEntity.actuallyHurt} without
 * calling it.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Unique private float msed$healthBefore;

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void msed$beforeHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        msed$healthBefore = self.getHealth() + self.getAbsorptionAmount();
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void msed$afterHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        float lost = msed$healthBefore - (player.getHealth() + player.getAbsorptionAmount());
        if (lost <= 0) return;
        // Prefer the attacker, for example the skeleton, over the projectile it fired.
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        String attackerType = attacker != null
                ? BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString()
                : null;
        String damageType = source.typeHolder()
                .unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(source.getMsgId());
        ServerPlayNetworking.send(player, new PlayerDamagedS2CPayload(lost, damageType, attackerType));
    }
}
