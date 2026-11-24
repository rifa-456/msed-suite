package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.proto.v1.ObservationStat;
import java.util.List;

/** A collector that reports list counts for the episode metadata's {@code observation_stats}. */
public interface ObservationSource {
    /**
     * Returns the largest count of each list this collector measures.
     *
     * @return one entry per measured list
     */
    List<ObservationStat> getObservationStats();
}
