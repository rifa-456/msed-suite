package io.github.rifa456.msed.events;

import org.jetbrains.annotations.Nullable;

/**
 * The cause of the latest damage the player took, as the client keeps it until a death event is
 * recorded. It stores registry names instead of the server's damage source object.
 *
 * @param type the damage type's registry name, for example {@code minecraft:fall}
 * @param entityId the entity type's registry name of the attacker, or {@code null} if no entity
 *     caused the damage
 */
public record ClientDamageSource(String type, @Nullable String entityId) {}
