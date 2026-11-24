package io.github.rifa456.msed.action;

import io.github.rifa456.msed.proto.v1.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The raw inputs and camera movement received since the last tick was collected.
 *
 * <p>Input callbacks and the tick both run on the client thread. An input that arrives after
 * tick {@code n-1} is collected and before tick {@code n} is collected belongs to tick {@code n}.
 * Events received from other threads follow the same rule.
 */
public final class InputRecorder {
    private static final ConcurrentLinkedQueue<InputEvent> INPUTS = new ConcurrentLinkedQueue<>();
    private static double mouseDx;
    private static double mouseDy;

    private InputRecorder() {}

    /**
     * Queues a raw input for the next tick.
     *
     * @param event the input as the game received it
     */
    public static void record(InputEvent event) {
        INPUTS.add(event);
    }

    /**
     * Adds the camera movement applied in one frame to the total for the next tick. Call this on
     * the client thread.
     *
     * @param dx the horizontal movement in this frame
     * @param dy the vertical movement in this frame
     */
    public static void addMouseMovement(double dx, double dy) {
        mouseDx += dx;
        mouseDy += dy;
    }

    /**
     * Removes and returns the inputs queued since the previous call.
     *
     * @return the inputs in the order they arrived, empty if there were none
     */
    public static List<InputEvent> drainInputs() {
        List<InputEvent> out = new ArrayList<>();
        InputEvent e;
        while ((e = INPUTS.poll()) != null) {
            out.add(e);
        }
        return out;
    }

    /**
     * Returns the camera movement since the previous call and sets the totals back to zero. Call
     * this on the client thread.
     *
     * @return a two-element array of {@code dx} then {@code dy}, both zero if the camera did not
     *     move
     */
    public static double[] drainMouseMovement() {
        double[] out = {mouseDx, mouseDy};
        mouseDx = 0;
        mouseDy = 0;
        return out;
    }

    /** Discards the inputs and camera movement buffered before a recording starts. */
    public static void reset() {
        INPUTS.clear();
        mouseDx = 0;
        mouseDy = 0;
    }
}
