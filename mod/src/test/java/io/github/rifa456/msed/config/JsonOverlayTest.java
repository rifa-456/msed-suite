package io.github.rifa456.msed.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class JsonOverlayTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void missingKeysKeepTheirDefaults() {
        JsonObject base = json("{\"vision\": {\"width\": 640, \"height\": 360}, \"fps\": 20}");
        JsonOverlay.apply(base, json("{\"vision\": {\"width\": 1280}}"));
        assertEquals(json("{\"vision\": {\"width\": 1280, \"height\": 360}, \"fps\": 20}"), base);
    }

    @Test
    void falseAndZeroFromTheFileWin() {
        JsonObject base = json("{\"record\": true, \"grace\": 3}");
        JsonOverlay.apply(base, json("{\"record\": false, \"grace\": 0}"));
        assertEquals(json("{\"record\": false, \"grace\": 0}"), base);
    }
}
