package io.github.rifa456.msed.recording;

import io.github.rifa456.msed.config.ConfigFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The id that marks which player recorded an episode. It stays the same across sessions when the
 * config enables a persistent id, and is new for each session otherwise. The id is a random UUID
 * and holds no account data.
 */
public class UserId {
    private static final Logger LOG = LoggerFactory.getLogger(UserId.class);

    private static final Path USER_ID_FILE =
            Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("msed_user.id");

    /**
     * Returns the user id for a new session. With a persistent id enabled, reads it from
     * {@code config/msed_user.id} in the game folder, and creates that file on first use. If the
     * file cannot be read or written, returns a new random id for this session only.
     *
     * @return the user id, a UUID string
     */
    public static String getUserId() {
        if (!ConfigFile.getConfig().getMetadata().getEnablePersistentUserId()) {
            return UUID.randomUUID().toString();
        }

        try {
            if (Files.exists(USER_ID_FILE)) {
                String userId =
                        Files.readString(USER_ID_FILE, StandardCharsets.UTF_8).trim();
                if (!userId.isEmpty()) {
                    return userId;
                }
            }
            String newUserId = UUID.randomUUID().toString();
            Files.writeString(USER_ID_FILE, newUserId, StandardCharsets.UTF_8);
            LOG.info("Created a persistent user id: {}", newUserId);
            return newUserId;
        } catch (IOException e) {
            LOG.error("Cannot read or write the persistent user id file; using a new id for this session only", e);
            return UUID.randomUUID().toString();
        }
    }
}
