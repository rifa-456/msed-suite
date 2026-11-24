package io.github.rifa456.msed.events;

import io.github.rifa456.msed.Msed;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The packet the integrated server sends to the client when the player loses health.
 *
 * @param amount the health and absorption lost, always positive
 * @param damageType the damage type's registry name, for example {@code minecraft:arrow}
 * @param sourceEntityId the entity type's registry name of the attacker, or {@code null} if no
 *     entity caused the damage
 */
public record PlayerDamagedS2CPayload(
        float amount, String damageType, @Nullable String sourceEntityId) implements CustomPacketPayload {
    /** The packet type, {@code msed:player_damaged}. */
    public static final CustomPacketPayload.Type<PlayerDamagedS2CPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Msed.MOD_ID, "player_damaged"));
    /** The wire format: the amount, the damage type, then the attacker type if there is one. */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerDamagedS2CPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeFloat(payload.amount());
                buf.writeUtf(payload.damageType());
                buf.writeOptional(Optional.ofNullable(payload.sourceEntityId()), FriendlyByteBuf::writeUtf);
            },
            (buf) -> new PlayerDamagedS2CPayload(
                    buf.readFloat(),
                    buf.readUtf(),
                    buf.readOptional(FriendlyByteBuf::readUtf).orElse(null)));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
