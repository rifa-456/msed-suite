package io.github.rifa456.msed;

import io.github.rifa456.msed.events.ItemPickedUpS2CPayload;
import io.github.rifa456.msed.events.PlayerDamagedS2CPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's common entry point. It registers the packets the integrated server sends to the
 * client with damage and pickup events.
 */
public class Msed implements ModInitializer {
    /** The mod id, also the namespace of every MSED identifier. */
    public static final String MOD_ID = "msed";

    private static final Logger LOG = LoggerFactory.getLogger(Msed.class);

    /**
     * Returns the SemVer this mod was released as, read from {@code fabric.mod.json}.
     *
     * @return the version string, for example {@code 1.2.0}
     * @throws java.util.NoSuchElementException if Fabric Loader has not loaded this mod
     */
    public static String version() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(ItemPickedUpS2CPayload.ID, ItemPickedUpS2CPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerDamagedS2CPayload.ID, PlayerDamagedS2CPayload.CODEC);
        LOG.info("MSED initialized");
    }
}
