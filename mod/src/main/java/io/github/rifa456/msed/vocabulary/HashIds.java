package io.github.rifa456.msed.vocabulary;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

/**
 * Ids for values that no game registry numbers, such as screens, tags and events. The id is the
 * Murmur3 128-bit hash of the value's string, truncated to 32 bits, so the same string gets the
 * same id in every session and on every machine.
 */
public final class HashIds {
    private HashIds() {}

    /**
     * Returns the hash id of a string.
     *
     * @param identifier the value's name, for example a screen class or {@code event.death}
     * @return the first 32 bits of the string's Murmur3 128-bit hash, as UTF-8
     */
    public static int of(String identifier) {
        return Hashing.murmur3_128()
                .hashString(identifier, StandardCharsets.UTF_8)
                .asInt();
    }
}
