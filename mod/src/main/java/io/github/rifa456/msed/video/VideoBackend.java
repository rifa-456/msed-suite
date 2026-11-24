package io.github.rifa456.msed.video;

import io.github.rifa456.msed.config.ConfigFile;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The FFmpeg executable and encoder every session records video with. Both are found once, on a
 * background thread at client start, so the search never delays the game. A session that starts
 * before the search ends waits for it.
 */
public final class VideoBackend {
    private static final Logger LOG = LoggerFactory.getLogger(VideoBackend.class);

    /**
     * The FFmpeg executable and the encoder chosen for it.
     *
     * @param ffmpeg the FFmpeg executable
     * @param profile the encoder and its output arguments
     */
    public record Encoding(Path ffmpeg, CodecDiscovery.CodecProfile profile) {}

    private static @Nullable VideoBackend instance;
    private final CompletableFuture<Optional<Encoding>> backend;

    private VideoBackend() {
        this.backend = CompletableFuture.supplyAsync(VideoBackend::resolve);
    }

    /** Starts the search for FFmpeg and an encoder. Call once, at client start. */
    public static void init() {
        instance = new VideoBackend();
    }

    /**
     * Returns the backend started by {@link #init}.
     *
     * @return the backend
     * @throws NullPointerException if {@link #init} has not run
     */
    public static VideoBackend getInstance() {
        return Objects.requireNonNull(instance, "VideoBackend.init() has not run");
    }

    private static Optional<Encoding> resolve() {
        try {
            return FFmpegLocator.find()
                    .map(ffmpeg -> new Encoding(
                            ffmpeg,
                            CodecDiscovery.select(ffmpeg, ConfigFile.getConfig().getFfmpeg())));
        } catch (Exception e) {
            LOG.error("Video recording is disabled", e);
            return Optional.empty();
        }
    }

    /**
     * Returns the FFmpeg executable and encoder, waiting for the search to finish if it has not.
     *
     * @return the encoding, or empty if FFmpeg is missing or no encoder works
     */
    public Optional<Encoding> encoding() {
        return backend.join();
    }

    /**
     * Starts an FFmpeg process that encodes one video stream into a file. Waits for the search to
     * finish if it has not.
     *
     * @param name the stream's name in logs and thread names, for example {@code World}
     * @param outputPath the video file to write
     * @param width the frame width in pixels, rounded down to an even number
     * @param height the frame height in pixels, rounded down to an even number
     * @return the running encoder
     * @throws IllegalStateException if FFmpeg is missing or no encoder works
     */
    public VideoEncoder createEncoder(String name, Path outputPath, int width, int height) {
        Encoding b = encoding().orElseThrow(() -> new IllegalStateException("FFmpeg is not available"));
        return new VideoEncoder(name, outputPath, width, height, b);
    }
}
