package io.github.rifa456.msed.video;

import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Frames on their way to FFmpeg's standard input, as one continuous byte stream. The encoder's
 * submitter thread puts filled buffers in, and the thread that feeds FFmpeg reads them out. Each
 * buffer goes back to the encoder's pool once it is fully read. At most
 * {@value VideoEncoder#BUFFER_COUNT} frames wait here.
 */
final class FramePipe extends InputStream {
    /** The end-of-stream marker. It is empty, which no frame ever is. */
    private static final byte[] END_OF_STREAM = new byte[0];
    /** How long ending the stream waits for room behind the queued frames. */
    private static final long CLOSE_WAIT_SECONDS = 10;

    private final BlockingQueue<byte[]> workQueue = new ArrayBlockingQueue<>(VideoEncoder.BUFFER_COUNT);
    private final BlockingQueue<byte[]> returnPool;
    private boolean finished = false;
    private byte @Nullable [] currentBuffer;
    private int currentOffset = 0;

    FramePipe(BlockingQueue<byte[]> returnPool) {
        this.returnPool = returnPool;
    }

    /**
     * Appends a frame to the stream, waiting while the queue is full.
     *
     * @param buffer the frame's RGB bytes, taken from the encoder's pool
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    void put(byte[] buffer) throws InterruptedException {
        workQueue.put(buffer);
    }

    /**
     * Ends the stream after the frames already queued. The reader sees the end once it has read
     * them all. Waits up to {@value #CLOSE_WAIT_SECONDS} seconds while the queue is full, which it
     * stays when FFmpeg has stopped reading.
     *
     * @return {@code true} if the end was queued
     */
    boolean closeStream() {
        try {
            return workQueue.offer(END_OF_STREAM, CLOSE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public int read() {
        throw new UnsupportedOperationException("Byte-by-byte read not supported");
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) {
        if (finished) return -1;
        int bytesRead = 0;
        while (len > 0) {
            if (currentBuffer == null) {
                try {
                    byte[] next = workQueue.take();
                    if (next.length == 0) {
                        finished = true;
                        return bytesRead > 0 ? bytesRead : -1;
                    }
                    currentBuffer = next;
                    currentOffset = 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return bytesRead > 0 ? bytesRead : -1;
                }
            }
            int toCopy = Math.min(currentBuffer.length - currentOffset, len);
            System.arraycopy(currentBuffer, currentOffset, b, off, toCopy);
            off += toCopy;
            len -= toCopy;
            currentOffset += toCopy;
            bytesRead += toCopy;
            if (currentOffset >= currentBuffer.length) {
                returnPool.add(currentBuffer);
                currentBuffer = null;
            }
        }
        return bytesRead;
    }
}
