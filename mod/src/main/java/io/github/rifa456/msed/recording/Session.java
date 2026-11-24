package io.github.rifa456.msed.recording;

import com.google.protobuf.Timestamp;
import io.github.rifa456.msed.Msed;
import io.github.rifa456.msed.action.InputRecorder;
import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.episode.EpisodeFiles;
import io.github.rifa456.msed.episode.MetadataFile;
import io.github.rifa456.msed.episode.ShardQueue;
import io.github.rifa456.msed.proto.v1.EpisodeMetadata;
import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.ObservationStat;
import io.github.rifa456.msed.proto.v1.ShardInfo;
import io.github.rifa456.msed.proto.v1.TickData;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import io.github.rifa456.msed.vocabulary.VocabularyWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One recording: an episode folder with its shards, videos, vocabulary and metadata.
 *
 * <p>A session runs once. {@link #start} opens the video streams, {@link #tick} records one tick
 * record per client tick, and {@link #stop} writes the terminal tick and the episode files. Call
 * every method on the client thread.
 */
public final class Session {
    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    /**
     * Format of the episode id, which names the episode folder. Milliseconds are included so that
     * two sessions started within one second never share a folder.
     */
    private static final DateTimeFormatter EPISODE_ID =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneId.systemDefault());

    private final String episodeId;
    private final String sessionId = UUID.randomUUID().toString();
    private final String userId;
    private final Instant startTime = Instant.now();
    private final MsedConfig config;
    private final Path episodeDir;
    /** Seed of the singleplayer world, or {@code null} on a remote server, which never sends it. */
    private final @Nullable Long worldSeed;

    private final DataCollector collector = new DataCollector();
    private final ShardQueue shardQueue;
    private final FrameSync frames;
    private SessionVideo video = SessionVideo.none();
    private long tickCounter;
    private boolean active;
    private boolean writerFailureShown;

    /**
     * Creates a session and its episode folder, and starts the shard writer thread. Takes a
     * snapshot of the config, which stays fixed for the whole session.
     *
     * @param userId the id written to the episode metadata as the player who recorded it
     * @throws java.io.UncheckedIOException if the episode folder cannot be created
     */
    public Session(String userId) {
        this.userId = userId;
        this.episodeId = EPISODE_ID.format(startTime);
        this.config = ConfigFile.getConfig();
        this.episodeDir = Path.of(config.getFilesystem().getDatasetRoot()).resolve(episodeId);
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.worldSeed =
                server != null ? server.getWorldData().worldGenOptions().seed() : null;
        this.shardQueue = new ShardQueue(episodeDir, config.getAdvanced());
        this.frames = new FrameSync(collector.getVisionCollector(), shardQueue);
        ConfigFile.setSessionSnapshot(config);
    }

    /**
     * Tells whether this session is recording.
     *
     * @return {@code true} between {@link #start} and {@link #stop}
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the collector that builds this session's tick records.
     *
     * @return the collector
     */
    public DataCollector getCollector() {
        return collector;
    }

    /**
     * Queues an event for this session's next tick record.
     *
     * @param event the event, without a tick number
     */
    public void logEvent(Event.Builder event) {
        collector.logEvent(event.build());
    }

    /**
     * Starts recording: resets the view, opens the enabled video streams, clears the vocabulary
     * and the input buffer, and prepares every collector. Does nothing if this session already
     * started. May block until FFmpeg detection, which starts with the client, has finished.
     *
     * <p>The keys that change the view are blocked while recording, so the view is set here to
     * what the player can keep: the HUD shown, the debug screen closed, first person, no smooth
     * camera.
     *
     * @param client the client whose state is recorded
     */
    public void start(Minecraft client) {
        if (active) return;
        active = true;
        LOG.info("Starting session {}", episodeId);
        client.options.hideGui = false;
        client.options.smoothCamera = false;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.debugEntries.setOverlayVisible(false);
        video = SessionVideo.start(config, episodeDir, client);
        collector.getVisionCollector().setEncoders(video.world(), video.gui());
        Vocabulary.getInstance().resetCache();
        InputRecorder.reset();
        Recorder.getInstance().setLastDamageSource(null);
        collector.startSession(client);
    }

    /**
     * Tells whether the frame being rendered is the latest tick's world-only frame.
     *
     * @return {@code true} if the frame must be captured now
     */
    boolean tryConsumeWorldCapture() {
        return frames.consumeWorldFlag();
    }

    /**
     * Tells whether the frame being rendered is the latest tick's composited frame.
     *
     * @return {@code true} if the frame must be captured now
     */
    boolean tryConsumeGuiCapture() {
        return frames.consumeGuiFlag();
    }

    /**
     * Records one tick. With video, the tick record waits for its frame and is written when the
     * frame renders; without video, it goes to the shard writer at once. A tick without a player
     * writes no record but still advances the tick number.
     *
     * @param client the client whose state is recorded
     */
    public void tick(Minecraft client) {
        if (!active) return;
        if (!shardQueue.isWriting() && !writerFailureShown) {
            writerFailureShown = true;
            StatusMessages.send(Component.translatable("text.msed.hud.writer_failed"));
        }
        frames.flush(tickCounter);
        tickCounter++;
        ProfilerFiller profiler = Profiler.get();
        profiler.push("msed:session_tick");
        TickData tick = collector.collectTick(client, tickCounter, profiler);
        if (tick != null) {
            if (video.any()) {
                frames.submit(tick);
            } else {
                shardQueue.submit(tick);
            }
        }
        profiler.pop();
    }

    /**
     * Stops recording and writes the episode: the terminal tick, the last shard, the vocabulary
     * and the metadata. Blocks the client thread until the last frames are handed to FFmpeg and
     * the shard writer has finished, up to about 30 seconds. The video files are finished on a background thread, so they may still be open
     * when this returns. Does nothing if this session is not active.
     */
    public void stop() {
        if (!active) return;
        active = false;
        LOG.info("Stopping session {}", episodeId);
        Instant stopTime = Instant.now();
        Minecraft client = Minecraft.getInstance();
        writeTerminalTick(client);
        collector.getVisionCollector().finishVideo();
        List<ObservationStat> observationStats = collector.getFinalStats();
        collector.endSession(client);
        ConfigFile.setSessionSnapshot(null);
        List<ShardInfo> shards = shardQueue.shutdown();
        video.finishInBackground();
        writeMetadata(stopTime, shards, observationStats);
    }

    /**
     * Undoes a {@link #start} that failed partway: finishes the video streams that opened, stops
     * the shard writer and releases the config snapshot. The episode folder is removed when
     * nothing was written to it.
     */
    void abort() {
        active = false;
        video.finishInBackground();
        shardQueue.shutdown();
        ConfigFile.setSessionSnapshot(null);
        try {
            Files.deleteIfExists(episodeDir);
        } catch (IOException e) {
            LOG.warn("Kept the folder of the failed episode {}: {}", episodeId, e.getMessage());
        }
    }

    /**
     * Closes the episode with one last tick record. Events logged after the last regular
     * collection, the stop event included, land in this tick, so every event appears in exactly
     * one tick. This tick also gets a frame, a copy of the previous one, so every tick references a
     * frame that both videos have.
     */
    private void writeTerminalTick(Minecraft client) {
        frames.flush(tickCounter);
        tickCounter++;
        TickData terminal = collector.collectTick(client, tickCounter, Profiler.get());
        if (terminal == null) {
            // No player, for example when stopped while leaving the world: keep the events only.
            terminal = TickData.newBuilder()
                    .setTick(tickCounter)
                    .setTimestamp(System.currentTimeMillis() / 1000.0)
                    .addAllEvents(collector.drainEvents(tickCounter))
                    .build();
        }
        shardQueue.submit(terminal);
        collector.getVisionCollector().repeatLastFrame();
        LOG.info("Terminal tick {} written with {} event(s)", tickCounter, terminal.getEventsCount());
    }

    private void writeMetadata(Instant stopTime, List<ShardInfo> shards, List<ObservationStat> stats) {
        VocabularyWriter.write(episodeDir);
        EpisodeMetadata.Builder metadata = EpisodeMetadata.newBuilder()
                .setEpisodeId(episodeId)
                .setSessionId(sessionId)
                .setUserId(userId)
                .setTaskDescription(config.getMetadata().getTaskDescription())
                .setMsedVersion(Msed.version())
                .setMinecraftVersion(SharedConstants.getCurrentVersion().name())
                .setStartTime(timestamp(startTime))
                .setEndTime(timestamp(stopTime))
                .setRecordingConfig(config)
                .addAllShards(shards)
                .addAllObservationStats(stats)
                .setWorldVideoPath(video.world() != null ? EpisodeFiles.WORLD_VIDEO : "")
                .setGuiVideoPath(video.gui() != null ? EpisodeFiles.GUI_VIDEO : "");
        if (worldSeed != null) {
            metadata.setWorldSeed(worldSeed);
        }
        try {
            MetadataFile.write(episodeDir, metadata.build());
            LOG.info("Metadata saved for episode {}", episodeId);
        } catch (IOException e) {
            LOG.error("Failed to write metadata for episode {}", episodeId, e);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
