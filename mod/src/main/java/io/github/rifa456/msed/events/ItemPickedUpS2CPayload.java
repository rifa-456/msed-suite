package io.github.rifa456.msed.events;

import io.github.rifa456.msed.Msed;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * The packet the integrated server sends to the client when the player picks up items.
 *
 * @param itemId the item's registry name
 * @param count the number of items that went into the inventory, always positive
 */
public record ItemPickedUpS2CPayload(Identifier itemId, int count) implements CustomPacketPayload {
    /** The packet type, {@code msed:item_picked_up}. */
    public static final CustomPacketPayload.Type<ItemPickedUpS2CPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Msed.MOD_ID, "item_picked_up"));
    /** The wire format: the item's registry name, then the count as a var-int. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPickedUpS2CPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeIdentifier(payload.itemId());
                buf.writeVarInt(payload.count());
            },
            (buf) -> new ItemPickedUpS2CPayload(buf.readIdentifier(), buf.readVarInt()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
