package io.github.rifa456.msed.observation.vision;

import io.github.rifa456.msed.video.VideoEncoder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One recorded video stream, with exactly one frame per tick, in tick order. Frame {@code n} of
 * the file belongs to the tick whose {@code VideoRef.frame_index} is {@code n}. A tick without a
 * usable render gets the previous frame again, with a logged warning, never a gap. All methods
 * run on the render thread.
 *
 * <p>A frame reaches the encoder one capture after its readback starts, so the GPU transfer
 * overlaps with game work. When the encoder has no free buffer, frames wait as owed instead of
 * blocking the render thread.
 */
final class FrameStream {
    private static final Logger LOG = LoggerFactory.getLogger(FrameStream.class);

    private final String name;
    private final VideoEncoder encoder;
    private final RgbCapture capture = new RgbCapture();
    private final byte[] lastFrame;
    private boolean hasFrame = false;
    /** Frames assigned to ticks so far, which is also the index of the next tick's frame. */
    private long assigned = 0;
    /** Frames due to the encoder but not handed over yet; each is sent as a copy of the latest frame. */
    private int owed = 0;
    /** Frames handed to the encoder so far, which is also the file index of the next one. */
    private long submitted = 0;
    /** The highest frame index already logged as stale, so that each stale frame is logged once. */
    private long lastStaleLogged = -1;
    /** Whether FFmpeg's exit has been logged, so that it is logged once. */
    private boolean encoderExitLogged = false;

    /**
     * Creates a stream and allocates its GPU resources, on the render thread.
     *
     * @param name the stream's name in log messages
     * @param encoder the encoder that writes this stream's video file
     * @param width the frame width in pixels
     * @param height the frame height in pixels
     */
    FrameStream(String name, VideoEncoder encoder, int width, int height) {
        this.name = name;
        this.encoder = encoder;
        this.lastFrame = new byte[width * height * 3];
        capture.resize(width, height);
    }

    /**
     * Returns the file index of the frame that the next capture or repeat assigns. The tick that
     * owns the frame stores it as {@code VideoRef.frame_index}.
     *
     * @return the index of the next frame, starting at 0
     */
    long nextFrameIndex() {
        return assigned;
    }

    /**
     * Captures the current render as the frame of the tick that requested it. When there is no
     * render target to read, the tick gets the previous frame again.
     *
     * @param client the client whose main render target is read
     */
    void capture(Minecraft client) {
        assigned++;
        if (capture.startReadback(client)) {
            collect(1);
        } else {
            LOG.warn("[{}] No render target to capture; frame {} repeats the previous frame", name, assigned - 1);
            collect(0);
            owed++;
            pay(false);
        }
    }

    /** Gives the current tick a copy of the previous frame, for a tick without a render. */
    void repeat() {
        assigned++;
        collect(0);
        owed++;
        pay(false);
    }

    /**
     * Hands every remaining frame to the encoder, waiting for free buffers, and releases the GPU
     * resources of this stream. Call it once, at the end of the recording.
     */
    void finish() {
        collect(0);
        pay(true);
        capture.destroy();
    }

    /**
     * Collects finished readbacks, oldest first, until at most {@code keep} remain in flight. A
     * readback that cannot be mapped repeats the previous frame.
     *
     * @param keep the number of readbacks to leave in flight
     */
    private void collect(int keep) {
        while (capture.pendingCount() > keep) {
            if (capture.finishOldest(lastFrame)) {
                hasFrame = true;
            } else {
                LOG.warn(
                        "[{}] Readback could not be mapped; frame {} repeats the previous frame",
                        name,
                        submitted + owed);
            }
            owed++;
            pay(false);
        }
    }

    /**
     * Sends owed frames to the encoder, each as a copy of the latest frame. Without a free buffer
     * the frames stay owed rather than blocking the render thread. Before the first real frame,
     * nothing is sent unless {@code force} is set.
     *
     * @param force whether to wait for free buffers and to send frames even before the first real
     *     frame, as black images
     */
    private void pay(boolean force) {
        while (owed > 0 && (hasFrame || force)) {
            byte[] buffer = encoder.getFreeBuffer();
            if (buffer == null && !encoder.isAlive()) {
                logEncoderExit();
                return;
            }
            if (buffer == null) {
                if (!force) {
                    logStale();
                    return;
                }
                buffer = encoder.awaitFreeBuffer();
                if (buffer == null) {
                    LOG.warn(
                            "[{}] No free buffer at the end of the recording; frames {} to {} are missing from the video",
                            name,
                            submitted,
                            submitted + owed - 1);
                    return;
                }
            }
            System.arraycopy(lastFrame, 0, buffer, 0, lastFrame.length);
            encoder.submit(buffer);
            submitted++;
            owed--;
        }
    }

    /**
     * Logs once that FFmpeg exited. No frame reaches the video after that, so the frames of later
     * ticks point past its end.
     */
    private void logEncoderExit() {
        if (!encoderExitLogged) {
            encoderExitLogged = true;
            LOG.error("[{}] FFmpeg exited; the video ends at frame {}", name, submitted);
        }
    }

    /**
     * Logs each owed frame not logged yet. Without a free buffer, an owed frame loses its own
     * image and will carry a later frame's image instead.
     */
    private void logStale() {
        for (long frame = Math.max(submitted, lastStaleLogged + 1); frame < submitted + owed; frame++) {
            LOG.warn("[{}] Encoder is falling behind; frame {} will carry a later frame's image", name, frame);
            lastStaleLogged = frame;
        }
    }
}
