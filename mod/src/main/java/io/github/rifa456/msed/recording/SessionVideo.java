package io.github.rifa456.msed.recording;

import io.github.rifa456.msed.episode.EpisodeFiles;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.RecordingSettings;
import io.github.rifa456.msed.video.FFmpegLocator;
import io.github.rifa456.msed.video.VideoBackend;
import io.github.rifa456.msed.video.VideoEncoder;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The video streams of one session: a world-only encoder and a GUI-composited encoder. Either one
 * is absent when the config disables it, and both are absent when FFmpeg is missing.
 */
final class SessionVideo {
    private static final Logger LOG = LoggerFactory.getLogger(SessionVideo.class);

    /** Daemon threads that wait for FFmpeg to finish each video after a session stops. */
    private static final ExecutorService FINISHER = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "MSED-Video-Finisher");
        thread.setDaemon(true);
        return thread;
    });

    private final @Nullable VideoEncoder world;
    private final @Nullable VideoEncoder gui;

    private SessionVideo(@Nullable VideoEncoder world, @Nullable VideoEncoder gui) {
        this.world = world;
        this.gui = gui;
    }

    /**
     * Returns a value with no stream, for a session before it starts or one that records no video.
     *
     * @return a value with neither encoder
     */
    static SessionVideo none() {
        return new SessionVideo(null, null);
    }

    /**
     * Starts the encoders for the streams enabled in the config. Without a usable FFmpeg, the
     * session records tick records only, and the player gets a chat message with the install
     * command. Blocks until FFmpeg detection, which starts with the client, has finished.
     *
     * @param config the session's config snapshot
     * @param episodeDir the episode folder the video files go in
     * @param client the client, for the chat message to the player
     * @return the started streams, or none
     */
    static SessionVideo start(MsedConfig config, Path episodeDir, Minecraft client) {
        RecordingSettings.Vision wanted = config.getRecording().getRecordVision();
        if (!wanted.getRecordPureWorldRgb() && !wanted.getRecordGuiWorldRgb()) {
            return none();
        }
        VideoBackend backend = VideoBackend.getInstance();
        if (backend.encoding().isEmpty()) {
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.translatable("text.msed.hud.ffmpeg_missing", FFmpegLocator.installHint()), false);
            }
            return none();
        }
        int width = config.getVision().getRgbWidth();
        int height = config.getVision().getRgbHeight();
        VideoEncoder world = wanted.getRecordPureWorldRgb()
                ? backend.createEncoder("World", episodeDir.resolve(EpisodeFiles.WORLD_VIDEO), width, height)
                : null;
        VideoEncoder gui = wanted.getRecordGuiWorldRgb()
                ? backend.createEncoder("Gui", episodeDir.resolve(EpisodeFiles.GUI_VIDEO), width, height)
                : null;
        return new SessionVideo(world, gui);
    }

    /**
     * Returns the world-only encoder.
     *
     * @return the encoder, or {@code null} if this session records no world-only video
     */
    @Nullable VideoEncoder world() {
        return world;
    }

    /**
     * Returns the GUI-composited encoder.
     *
     * @return the encoder, or {@code null} if this session records no composited video
     */
    @Nullable VideoEncoder gui() {
        return gui;
    }

    /**
     * Tells whether this session records at least one video stream.
     *
     * @return {@code true} if either encoder is present
     */
    boolean any() {
        return world != null || gui != null;
    }

    /**
     * Finishes both video files on background threads and returns at once, so the game keeps
     * running while FFmpeg flushes. The player gets a chat message as each file is finished.
     */
    void finishInBackground() {
        finishInBackground(world, "World");
        finishInBackground(gui, "Gui");
    }

    private static void finishInBackground(@Nullable VideoEncoder encoder, String name) {
        if (encoder == null) {
            return;
        }
        FINISHER.execute(() -> {
            try {
                encoder.shutdown();
                long frames = encoder.getCurrentFrameIndex();
                Minecraft.getInstance()
                        .execute(() -> StatusMessages.send(
                                Component.translatable("text.msed.hud.video_finalized", name, frames)));
            } catch (Exception e) {
                LOG.error("{} video failed during shutdown", name, e);
            }
        });
    }
}
