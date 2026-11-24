package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.proto.v1.ObservationKey;
import io.github.rifa456.msed.proto.v1.ObservationStat;
import java.util.concurrent.atomic.AtomicInteger;

/** The largest count of one kind of list in any tick of a session. Any thread may update it. */
public class StatTracker {
    private final ObservationKey key;
    private final AtomicInteger maxValue = new AtomicInteger(0);

    /**
     * Creates a tracker at zero.
     *
     * @param key the list being measured
     */
    public StatTracker(ObservationKey key) {
        this.key = key;
    }

    /**
     * Records this tick's count; the tracker keeps the largest.
     *
     * @param currentCount the number of entries in the list this tick
     */
    public void observe(int currentCount) {
        maxValue.getAndUpdate(existing -> Math.max(existing, currentCount));
    }

    /** Sets the count back to zero for a new session. */
    public void reset() {
        maxValue.set(0);
    }

    /**
     * Returns the result as the episode metadata stores it.
     *
     * @return the measured list and its largest count
     */
    public ObservationStat toProto() {
        return ObservationStat.newBuilder().setKey(key).setCount(maxValue.get()).build();
    }
}
