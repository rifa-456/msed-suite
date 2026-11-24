package io.github.rifa456.msed.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Lays the user's config file over the defaults key by key: a key missing from the file keeps its
 * default, and a value the user set, {@code false} and {@code 0} included, wins.
 */
final class JsonOverlay {
    private JsonOverlay() {}

    /**
     * Copies every key of {@code top} into {@code base}. Nested objects are merged key by key; any
     * other value replaces the one in {@code base}.
     *
     * @param base the defaults, changed in place
     * @param top the user's config file
     */
    static void apply(JsonObject base, JsonObject top) {
        for (Map.Entry<String, JsonElement> entry : top.entrySet()) {
            JsonElement existing = base.get(entry.getKey());
            if (existing != null && existing.isJsonObject() && entry.getValue().isJsonObject()) {
                apply(existing.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                base.add(entry.getKey(), entry.getValue());
            }
        }
    }
}
