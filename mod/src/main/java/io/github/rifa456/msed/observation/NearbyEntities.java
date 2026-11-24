package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.proto.v1.ItemStack;
import io.github.rifa456.msed.proto.v1.NearbyEntity;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The entities around the player: every entity, the player excluded, whose position is within
 * {@link #RADIUS} blocks of the player's position.
 */
public final class NearbyEntities {
    /** The distance in blocks within which an entity counts as nearby. */
    public static final double RADIUS = 16.0;

    private NearbyEntities() {}

    /**
     * Returns the entities near the player.
     *
     * @param player the player at the center
     * @return the entities other than the player whose position is within {@link #RADIUS} blocks
     */
    public static List<Entity> around(LocalPlayer player) {
        Vec3 center = player.position();
        return player.level()
                .getEntities(
                        player,
                        player.getBoundingBox().inflate(RADIUS),
                        entity -> entity.position().distanceToSqr(center) <= RADIUS * RADIUS);
    }

    /**
     * Returns one entity as the schema records it: its type, its position relative to
     * {@code origin}, its velocity and its rotation. A dropped item also records its stack.
     *
     * @param entity the entity to record
     * @param origin the point the position is relative to, the player's position when recording
     * @param vocabulary the vocabulary that gives the entity and item ids
     * @return the entity's record
     */
    public static NearbyEntity describe(Entity entity, Vec3 origin, Vocabulary vocabulary) {
        NearbyEntity.Builder builder = NearbyEntity.newBuilder()
                .setEntityId(vocabulary.getId(entity.getType()))
                .setPosition(Vectors.of(entity.position().subtract(origin)))
                .setVelocity(Vectors.of(entity.getDeltaMovement()))
                .setPitch(entity.getXRot())
                .setYaw(entity.getYRot());
        if (entity instanceof ItemEntity item) {
            net.minecraft.world.item.ItemStack stack = item.getItem();
            builder.setItemStack(ItemStack.newBuilder()
                    .setItemId(vocabulary.getId(stack.getItem()))
                    .setCount(stack.getCount())
                    .setDamage(stack.getDamageValue()));
        }
        return builder.build();
    }
}
