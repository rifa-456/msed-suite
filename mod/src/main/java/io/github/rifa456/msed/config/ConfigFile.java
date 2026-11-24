package io.github.rifa456.msed.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MSED settings, stored as {@code msed.json} in the game's config directory. Fields missing
 * from the file take their default value, and numbers outside their range are brought into it.
 */
public final class ConfigFile {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigFile.class);

    private static @Nullable MsedConfig instance;
    private static volatile @Nullable MsedConfig sessionSnapshot;
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("msed.json");

    private ConfigFile() {}

    /**
     * Reads the config file, or writes a default one if it does not exist. If the file cannot be
     * read, the defaults are used and the error is logged.
     */
    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            instance = defaults();
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            // Overlay the file on the defaults key by key. A field missing from the file keeps its
            // default, and a value the user set wins, false and 0 included.
            JsonObject merged = JsonParser.parseString(JsonFormat.printer()
                            .alwaysPrintFieldsWithNoPresence()
                            .print(defaults()))
                    .getAsJsonObject();
            JsonOverlay.apply(merged, JsonParser.parseString(json).getAsJsonObject());
            MsedConfig.Builder builder = MsedConfig.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(merged.toString(), builder);
            instance = ConfigLimits.apply(builder.build(), defaults());
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOG.error("Could not read the config file, using the defaults: {}", e.getMessage());
            instance = defaults();
        }
    }

    /**
     * Writes the config in memory to the config file. Does nothing before the first
     * {@link #load}. A write error is logged, not thrown.
     */
    public static void save() {
        MsedConfig config = instance;
        if (config == null) return;
        try {
            String json = JsonFormat.printer().alwaysPrintFieldsWithNoPresence().print(config);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            LOG.error("Could not write the config file: {}", e.getMessage());
        }
    }

    /**
     * Replaces the config in memory and writes it to the config file. A recording in progress
     * keeps its snapshot; the change applies from the next session.
     *
     * @param newConfig the config to keep
     */
    public static void update(MsedConfig newConfig) {
        instance = newConfig;
        save();
    }

    /**
     * Returns the config in effect, loading the file on the first call.
     *
     * <p>While recording, this is the snapshot taken when the session started. Edits made during
     * a session never change its episode, whose metadata stores that snapshot.
     *
     * @return the session snapshot while recording, otherwise the config in memory
     */
    public static MsedConfig getConfig() {
        MsedConfig snapshot = sessionSnapshot;
        if (snapshot != null) return snapshot;
        MsedConfig config = instance;
        if (config == null) {
            load();
            config = Objects.requireNonNull(instance);
        }
        return config;
    }

    /**
     * Freezes the config that {@link #getConfig} returns for a recording session. Any thread may
     * read the snapshot.
     *
     * @param snapshot the config for the session, or {@code null} when the session ends
     */
    public static void setSessionSnapshot(@Nullable MsedConfig snapshot) {
        sessionSnapshot = snapshot;
    }

    /**
     * Returns the default config. It fills fields missing from the file and backs the config
     * screen's reset buttons.
     *
     * @return a new default config
     */
    public static MsedConfig defaults() {
        return ConfigDefaults.build();
    }
}
