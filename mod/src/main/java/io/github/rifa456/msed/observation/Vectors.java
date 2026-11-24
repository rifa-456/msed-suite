package io.github.rifa456.msed.observation;

import net.minecraft.world.phys.Vec3;

/** Conversions from the game's vectors to the schema's. */
public final class Vectors {
    private Vectors() {}

    /**
     * Returns a game vector as the schema records it.
     *
     * @param vector the game vector
     * @return a vector with the same coordinates
     */
    public static io.github.rifa456.msed.proto.v1.Vec3 of(Vec3 vector) {
        return io.github.rifa456.msed.proto.v1.Vec3.newBuilder()
                .setX(vector.x)
                .setY(vector.y)
                .setZ(vector.z)
                .build();
    }
}
