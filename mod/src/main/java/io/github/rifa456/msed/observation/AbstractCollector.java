package io.github.rifa456.msed.observation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reads one modality of the game state at each tick. A subclass implements
 * {@link #collect}; this class runs it inside a profiler section and keeps the result where any
 * thread can read it.
 *
 * @param <T> the message the modality is recorded as
 */
public abstract class AbstractCollector<T> {

    private final AtomicReference<T> latest = new AtomicReference<>();

    /**
     * Prepares for a new recording and drops the previous session's result.
     *
     * @param client the client whose state is recorded
     */
    public void startSession(Minecraft client) {
        latest.set(null);
    }

    /**
     * Drops the session's result once the recording ends.
     *
     * @param client the client whose state was recorded
     */
    public void endSession(Minecraft client) {
        latest.set(null);
    }

    /**
     * Returns the name of this collector's section in Minecraft's profiler.
     *
     * @return the section name, for example {@code msed:hearing}
     */
    public abstract String getProfilerName();

    /**
     * Reads this modality from the current game state, on the client thread, once per tick.
     *
     * @param client the client whose state is recorded
     * @param profiler the profiler, for sections nested inside this collector's
     * @return the modality's message for this tick
     */
    protected abstract T collect(Minecraft client, ProfilerFiller profiler);

    /**
     * Collects this tick's message inside this collector's profiler section and keeps it.
     *
     * @param client the client whose state is recorded
     * @param profiler the profiler the section opens in
     */
    public final void collectWithProfiler(Minecraft client, ProfilerFiller profiler) {
        profiler.push(getProfilerName());
        try {
            T data = collect(client, profiler);
            latest.set(data);
        } finally {
            profiler.pop();
        }
    }

    /**
     * Returns the message of the latest collection in this session. Any thread may call this.
     *
     * @return the latest message
     * @throws NullPointerException if nothing was collected since the session started
     */
    public T getLatest() {
        return Objects.requireNonNull(latest.get(), "collect() has not run this session");
    }
}
