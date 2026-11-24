package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.proto.v1.ObservationKey;
import io.github.rifa456.msed.proto.v1.ObservationStat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * A collector that also measures some of its lists: for each, the largest count in any tick of
 * the session. The counts go into the episode metadata as {@code observation_stats}.
 *
 * @param <T> the message the modality is recorded as
 */
public abstract class TrackingCollector<T> extends AbstractCollector<T> implements ObservationSource {

    private final List<StatTracker> trackers = new ArrayList<>();

    /**
     * Starts measuring one kind of list. Subclasses call this from their constructor.
     *
     * @param key the list being measured
     * @return the tracker that each tick's count goes to
     */
    protected final StatTracker registerTracker(ObservationKey key) {
        StatTracker tracker = new StatTracker(key);
        trackers.add(tracker);
        return tracker;
    }

    @Override
    public void startSession(Minecraft client) {
        super.startSession(client);
        trackers.forEach(StatTracker::reset);
    }

    @Override
    public List<ObservationStat> getObservationStats() {
        return trackers.stream().map(StatTracker::toProto).toList();
    }
}
