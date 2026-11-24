package io.github.rifa456.msed.recording;

import com.google.protobuf.TextFormat;
import io.github.rifa456.msed.action.ActionCollector;
import io.github.rifa456.msed.observation.AbstractCollector;
import io.github.rifa456.msed.observation.GuiCollector;
import io.github.rifa456.msed.observation.HearingCollector;
import io.github.rifa456.msed.observation.InventoryCollector;
import io.github.rifa456.msed.observation.ObservationSource;
import io.github.rifa456.msed.observation.PlayerStateCollector;
import io.github.rifa456.msed.observation.vision.VisionCollector;
import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.ObservationStat;
import io.github.rifa456.msed.proto.v1.TickData;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds each tick record: runs every collector once and adds the events logged since the
 * previous tick. A new modality needs three changes here: a field, an entry in
 * {@code allCollectors}, and a line in {@link #collectTick}.
 */
public class DataCollector {
    private static final Logger LOG = LoggerFactory.getLogger(DataCollector.class);

    private final ActionCollector actionCollector = new ActionCollector();
    private final VisionCollector visionCollector = new VisionCollector();
    private final HearingCollector hearingCollector = new HearingCollector();
    private final GuiCollector guiCollector = new GuiCollector();
    private final PlayerStateCollector playerStateCollector = new PlayerStateCollector();
    private final InventoryCollector inventoryCollector = new InventoryCollector();

    private final List<AbstractCollector<?>> allCollectors = List.of(
            actionCollector, visionCollector, hearingCollector, guiCollector, playerStateCollector, inventoryCollector);

    private final ConcurrentLinkedQueue<Event> eventBuffer = new ConcurrentLinkedQueue<>();

    /**
     * Prepares every collector for a new recording.
     *
     * @param client the client whose state is recorded
     */
    public void startSession(Minecraft client) {
        allCollectors.forEach(c -> c.startSession(client));
    }

    /**
     * Ends the recording for every collector and discards events that never reached a tick.
     *
     * @param client the client whose state was recorded
     */
    public void endSession(Minecraft client) {
        allCollectors.forEach(c -> c.endSession(client));
        eventBuffer.clear();
    }

    /**
     * Queues an event for the next tick record. Any thread may call this; the event gets its tick
     * number when {@link #collectTick} or {@link #drainEvents} takes it from the queue.
     *
     * @param event the event, without a tick number
     */
    public void logEvent(Event event) {
        Vocabulary.getInstance().traceEvent(event);
        if (event.getPayloadCase() != Event.PayloadCase.PAYLOAD_NOT_SET) {
            String singleLinePayload =
                    TextFormat.printer().emittingSingleLine(true).printToString(event);
            LOG.info("[Event:{}] {}", event.getPayloadCase().name(), singleLinePayload);
        }
        eventBuffer.add(event);
    }

    /**
     * Collects one tick: every modality, and the events logged since the previous tick.
     *
     * @param client the client whose state is recorded
     * @param tickCount the tick's number in the episode, starting at 1
     * @param profiler the profiler the collectors open their sections in
     * @return the tick record, or {@code null} when there is no player to observe
     */
    public @Nullable TickData collectTick(Minecraft client, long tickCount, ProfilerFiller profiler) {
        if (client.player == null) {
            return null;
        }
        allCollectors.forEach(c -> c.collectWithProfiler(client, profiler));
        List<Event> events = drainEvents(tickCount);
        return TickData.newBuilder()
                .setTick(tickCount)
                .setTimestamp(System.currentTimeMillis() / 1000.0)
                .setAction(actionCollector.getLatest())
                .setPlayer(playerStateCollector.getLatest())
                .setInventory(inventoryCollector.getLatest())
                .setVision(visionCollector.getLatest())
                .setHearing(hearingCollector.getLatest())
                .setGui(guiCollector.getLatest())
                .addAllEvents(events)
                .build();
    }

    /**
     * Takes every queued event and stamps it with {@code tick}. An event logged between the
     * collections of tick n-1 and tick n belongs to tick n.
     *
     * @param tick the tick the events belong to
     * @return the events, in the order they were logged
     */
    public List<Event> drainEvents(long tick) {
        List<Event> events = new ArrayList<>();
        Event evt;
        while ((evt = eventBuffer.poll()) != null) {
            events.add(evt.toBuilder().setTick(tick).build());
        }
        return events;
    }

    /**
     * Returns the vision collector, which the render hooks hand each frame to.
     *
     * @return the vision collector
     */
    public VisionCollector getVisionCollector() {
        return visionCollector;
    }

    /**
     * Returns the list counts measured during the session, for the episode metadata.
     *
     * @return one entry per measured list, across all collectors
     */
    public List<ObservationStat> getFinalStats() {
        List<ObservationStat> allStats = new ArrayList<>();
        for (AbstractCollector<?> collector : allCollectors) {
            if (collector instanceof ObservationSource source) {
                allStats.addAll(source.getObservationStats());
            }
        }
        return allStats;
    }
}
