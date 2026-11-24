package io.github.rifa456.msed.video;

import com.github.kokorin.jaffree.LogLevel;
import com.github.kokorin.jaffree.StreamType;
import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.FFmpegResultFuture;
import com.github.kokorin.jaffree.ffmpeg.PipeInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import io.github.rifa456.msed.config.ConfigFile;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One video stream of a session, encoded by its own FFmpeg process into a fragmented MP4 file. The
 * render thread fills buffers from a pool of {@value #BUFFER_COUNT} and submits them; a submitter thread passes them
 * on to FFmpeg's standard input as raw RGB frames. The frame index of the next frame is known at
 * once, so a tick record can name its frame before FFmpeg has encoded it.
 */
public class VideoEncoder {
    private static final Logger LOG = LoggerFactory.getLogger(VideoEncoder.class);
    /** Frame buffers per stream: the pool, and the most frames waiting for FFmpeg at once. */
    static final int BUFFER_COUNT = 10;
    /** How long the end of a recording waits for FFmpeg to free a buffer. */
    private static final long FREE_BUFFER_WAIT_SECONDS = 10;
    /** How often that wait checks whether FFmpeg is still running. */
    private static final long FREE_BUFFER_POLL_MILLIS = 100;

    private final String name;
    private final Path outputPath;
    private final int width;
    private final int height;
    private final int fps;
    private final BlockingQueue<byte[]> freeBufferPool;
    private final FramePipe pipeInputStream;
    private final AtomicLong frameCounter = new AtomicLong(0);
    private volatile boolean running = true;
    private FFmpegResultFuture ffmpegFuture;
    private final ExecutorService submissionExecutor;
    private final VideoBackend.Encoding backend;

    /**
     * Starts the FFmpeg process. An odd width or height is rounded down to an even number, which
     * most encoders require. The frame rate comes from the config.
     *
     * @param name the stream's name in logs and thread names, for example {@code World}
     * @param outputPath the video file to write
     * @param width the frame width in pixels
     * @param height the frame height in pixels
     * @param backend the FFmpeg executable and encoder
     */
    public VideoEncoder(String name, Path outputPath, int width, int height, VideoBackend.Encoding backend) {
        this.name = name;
        this.backend = backend;
        this.outputPath = outputPath;
        this.width = width % 2 == 0 ? width : width - 1;
        this.height = height % 2 == 0 ? height : height - 1;
        this.fps = ConfigFile.getConfig().getFfmpeg().getFps();
        int frameSize = this.width * this.height * 3;
        this.freeBufferPool = new ArrayBlockingQueue<>(BUFFER_COUNT);
        for (int i = 0; i < BUFFER_COUNT; i++) {
            freeBufferPool.add(new byte[frameSize]);
        }
        this.pipeInputStream = new FramePipe(freeBufferPool);
        this.submissionExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "MSED-Frame-Submitter-" + name));

        startFFmpegAsync();
    }

    private void startFFmpegAsync() {
        CodecDiscovery.CodecProfile profile = backend.profile();
        UrlOutput output = UrlOutput.toPath(outputPath)
                .setCodec(StreamType.VIDEO, profile.codecName())
                .addArguments("-pix_fmt", "yuv420p")
                // One keyframe per second keeps seeking to any tick's frame cheap.
                .addArguments("-g", String.valueOf(fps))
                // Each fragment is complete on disk, so a crash loses only the last one.
                .addArguments("-movflags", "+frag_keyframe+empty_moov+default_base_moof");
        if (profile.codecName().startsWith("hevc")) {
            // hvc1 is the HEVC sample entry that browsers and Apple players accept.
            output.addArguments("-tag:v", "hvc1");
        }
        for (int i = 0; i + 1 < profile.arguments().size(); i += 2) {
            output.addArguments(profile.arguments().get(i), profile.arguments().get(i + 1));
        }
        FFmpeg ffmpeg = new FFmpeg(backend.ffmpeg())
                .addInput(PipeInput.pumpFrom(pipeInputStream)
                        .setFormat("rawvideo")
                        .setPixelFormat("rgb24")
                        .setFrameRate(fps)
                        .setFrameSize(width, height))
                .addOutput(output)
                .addArgument("-nostats")
                .setLogLevel(LogLevel.WARNING);

        this.ffmpegFuture = ffmpeg.executeAsync();
        LOG.info("Started {} video encoder, writing to {}", name, outputPath);
    }

    /**
     * Returns the number of frames accepted so far, which is also the index of the next frame in
     * the video. {@link #submit} updates it on the render thread before returning, so a tick that
     * reads it counts every frame submitted before it.
     *
     * @return the frame count
     */
    public long getCurrentFrameIndex() {
        return frameCounter.get();
    }

    /**
     * Returns the width of the encoded frames.
     *
     * @return the width in pixels, always even
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height of the encoded frames.
     *
     * @return the height in pixels, always even
     */
    public int height() {
        return height;
    }

    /**
     * Takes a free buffer from the pool without waiting, so the render thread never blocks on
     * FFmpeg. The buffer holds {@code width * height * 3} bytes.
     *
     * @return a free buffer, or {@code null} when every buffer is still queued for FFmpeg
     */
    public byte @Nullable [] getFreeBuffer() {
        return freeBufferPool.poll();
    }

    /**
     * Takes a free buffer from the pool, waiting while FFmpeg reads the frames before it. Use only
     * at the end of a recording, when a short wait for the last frame does not matter. Gives up
     * once FFmpeg has exited, since no buffer comes back after that, and after
     * {@value #FREE_BUFFER_WAIT_SECONDS} seconds.
     *
     * @return a free buffer, or {@code null} if FFmpeg exited, the wait ran out or the thread was
     *     interrupted
     */
    public byte @Nullable [] awaitFreeBuffer() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FREE_BUFFER_WAIT_SECONDS);
        try {
            while (System.nanoTime() < deadline) {
                byte[] buffer = freeBufferPool.poll(FREE_BUFFER_POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (buffer != null) return buffer;
                if (!isAlive()) return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Tells whether the FFmpeg process is still running. It stops early when it fails, for
     * example when the encoder cannot open another session or the disk is full.
     *
     * @return {@code true} until FFmpeg exits
     */
    public boolean isAlive() {
        return !ffmpegFuture.isDone();
    }

    /**
     * Puts back a buffer that was taken from the pool but not submitted.
     *
     * @param buffer the buffer
     */
    public void recycleBuffer(byte[] buffer) {
        // Every buffer comes from the pool, so the pool always has room for it.
        freeBufferPool.add(buffer);
    }

    /**
     * Submits a filled buffer as the next frame and returns without waiting for FFmpeg. An
     * accepted frame always reaches FFmpeg, because the submitter thread waits rather than drop
     * it, so the frame index stays in step with the video. The render thread is slowed only by
     * the fixed pool, through {@link #getFreeBuffer}. After {@link #shutdown}, the buffer goes
     * back to the pool and no frame is added.
     *
     * @param filledBuffer a buffer from the pool, filled with one RGB frame
     */
    public void submit(byte[] filledBuffer) {
        if (!running) {
            recycleBuffer(filledBuffer);
            return;
        }
        frameCounter.incrementAndGet();
        submissionExecutor.execute(() -> {
            try {
                pipeInputStream.put(filledBuffer);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("[{}] Interrupted while queueing a frame; the video may be short", name);
                recycleBuffer(filledBuffer);
            }
        });
    }

    /**
     * Passes every accepted frame to FFmpeg, ends the stream and waits for FFmpeg to finish the
     * file. Blocks for up to 30 seconds to pass the frames and 120 seconds more for FFmpeg, so call
     * it off the client thread. When FFmpeg runs out of time, it is asked to stop. A second call
     * does nothing.
     */
    public void shutdown() {
        if (!running) return;
        running = false;
        submissionExecutor.shutdown();
        try {
            if (!submissionExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                LOG.error("[{}] Timed out flushing frames to the encoder", name);
                submissionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!pipeInputStream.closeStream()) {
            LOG.error("[{}] FFmpeg stopped reading frames; the video ends early", name);
        }
        if (ffmpegFuture != null) {
            try {
                ffmpegFuture.get(120, TimeUnit.SECONDS);
                LOG.info("{} video finished cleanly ({} frames)", name, frameCounter.get());
            } catch (TimeoutException e) {
                LOG.error("{} video did not finish in time; stopping FFmpeg", name);
                ffmpegFuture.graceStop();
            } catch (Exception e) {
                LOG.error("{} video failed during final flush", name, e);
            }
        }
    }
}
