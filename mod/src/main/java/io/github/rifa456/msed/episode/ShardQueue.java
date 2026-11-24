package io.github.rifa456.msed.episode;

import io.github.rifa456.msed.proto.v1.AdvancedSettings;
import io.github.rifa456.msed.proto.v1.ShardInfo;
import io.github.rifa456.msed.proto.v1.TickData;
import io.github.rifa456.msed.proto.v1.TickRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shard files of one episode, written by a background writer thread. The client thread hands
 * over tick records through a bounded queue and never waits for disk. When the queue is full, the
 * tick is dropped and logged. A shard closes after {@code ticksPerShard} records and the next one
 * opens.
 */
public class ShardQueue {
    private static final Logger LOG = LoggerFactory.getLogger(ShardQueue.class);

    private final ExecutorService writerExecutor;
    /** Ticks waiting for the writer thread. An empty element tells the writer to finish. */
    private final BlockingQueue<Optional<TickData>> inputQueue;

    private final Path episodeDir;
    private final List<ShardInfo> producedShards = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;
    /** False once the writer thread has stopped on an error. No tick can be written after that. */
    private volatile boolean writerAlive = true;

    private boolean warnedWriterDead = false;
    private final int ticksPerShard;
    private final int queueCapacity;

    /** How long shutdown waits for room in the queue for the stop signal. */
    private static final long STOP_OFFER_SECONDS = 5;

    /** How long shutdown waits for the writer to finish the last shard. */
    private static final long STOP_WAIT_SECONDS = 15;

    /**
     * Creates the episode folder and starts the writer thread.
     *
     * @param episodeDir the episode folder; its name also names the writer thread
     * @param settings the shard size and the queue capacity of the session
     * @throws UncheckedIOException if the episode folder cannot be created
     */
    public ShardQueue(Path episodeDir, AdvancedSettings settings) {
        this.ticksPerShard = settings.getTicksPerShard();
        this.queueCapacity = settings.getWriterQueueCapacity();
        this.episodeDir = episodeDir;
        this.inputQueue = new LinkedBlockingQueue<>(this.queueCapacity);
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MSED-Writer-" + episodeDir.getFileName());
            t.setUncaughtExceptionHandler((th, e) -> LOG.error("MSED writer thread crashed", e));
            return t;
        });
        try {
            Files.createDirectories(this.episodeDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create episode directory: " + episodeDir, e);
        }
        this.writerExecutor.execute(this::processLoop);
    }

    /**
     * Tells whether ticks are still being saved. The writer stops for good when a write fails,
     * for example when the disk is full.
     *
     * @return {@code false} once the writer thread has stopped on an error
     */
    public boolean isWriting() {
        return writerAlive;
    }

    /**
     * Queues a tick record for the writer thread without blocking. The tick is dropped, with a
     * logged message, when the queue is full, when the writer has failed, or after
     * {@link #shutdown}.
     *
     * @param data the tick record
     */
    public void submit(TickData data) {
        if (!running) return;
        if (!writerAlive) {
            if (!warnedWriterDead) {
                LOG.error("Writer thread has stopped; ticks from {} on are not saved", data.getTick());
                warnedWriterDead = true;
            }
            return;
        }
        if (!inputQueue.offer(Optional.of(data))) {
            LOG.warn("Writer queue is full (capacity {}); dropping tick {}", queueCapacity, data.getTick());
        }
    }

    /**
     * Stops accepting ticks, lets the writer write every queued tick and close the last shard, and
     * stops the writer thread. Blocks the caller for up to 5 seconds to queue the stop signal, and
     * 15 seconds more for the writer to finish. When either wait runs out, the writer is
     * interrupted and the ticks still queued are lost.
     *
     * @return the shards written, in order, including one cut short by an error
     */
    public List<ShardInfo> shutdown() {
        this.running = false;
        try {
            // A dead or stuck writer leaves the queue full, so the stop signal waits only so long.
            if (!writerAlive || !inputQueue.offer(Optional.empty(), STOP_OFFER_SECONDS, TimeUnit.SECONDS)) {
                LOG.error("Writer thread is not draining the queue; forcing shutdown");
                writerExecutor.shutdownNow();
            }
            writerExecutor.shutdown();
            if (!writerExecutor.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
                LOG.error("Writer thread did not finish in time; forcing shutdown");
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writerExecutor.shutdownNow();
        }
        return new ArrayList<>(producedShards);
    }

    private void processLoop() {
        int shardIndex = 0;
        try {
            Optional<TickData> next = inputQueue.take();
            while (next.isPresent()) {
                Path shardPath = episodeDir.resolve(EpisodeFiles.shard(shardIndex++));
                try (ShardWriter writer = new ShardWriter(shardPath)) {
                    try {
                        do {
                            writer.write(
                                    TickRecord.newBuilder().setData(next.get()).build());
                            next = inputQueue.take();
                        } while (next.isPresent() && writer.getTickCount() < ticksPerShard);
                    } finally {
                        // List a shard even when writing stops partway, so its digest can still be checked.
                        producedShards.add(writer.finish());
                    }
                }
            }
        } catch (IOException e) {
            writerAlive = false;
            LOG.error("Shard writer failed; recording continues without saving ticks", e);
        } catch (InterruptedException e) {
            writerAlive = false;
            Thread.currentThread().interrupt();
        }
    }
}
