package io.github.rifa456.msed.episode;

import java.util.Locale;

/**
 * Names of the files in an episode folder. The toolkit's {@code msed._episode} package reads the
 * same names, so a change here needs the same change there.
 */
public final class EpisodeFiles {
    /** The episode manifest. */
    public static final String METADATA = "metadata.json";
    /** The map from the integer ids in tick records to names. */
    public static final String VOCABULARY = "vocabulary.json";
    /** The world-only video, without hand, HUD or screens. */
    public static final String WORLD_VIDEO = "world.mp4";
    /** The video as the player saw it, HUD and screens included. */
    public static final String GUI_VIDEO = "gui.mp4";

    private EpisodeFiles() {}

    /**
     * Returns the file name of a shard. Shards are numbered from 0 in tick order.
     *
     * @param index the shard's position in the episode
     * @return the file name, for example {@code shard_0003.bin}
     */
    public static String shard(int index) {
        return String.format(Locale.ROOT, "shard_%04d.bin", index);
    }
}
