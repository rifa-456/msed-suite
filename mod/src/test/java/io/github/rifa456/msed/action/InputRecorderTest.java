package io.github.rifa456.msed.action;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rifa456.msed.proto.v1.InputAction;
import io.github.rifa456.msed.proto.v1.InputEvent;
import io.github.rifa456.msed.proto.v1.KeyInput;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InputRecorderTest {
    private static InputEvent key(int code, InputAction action) {
        return InputEvent.newBuilder()
                .setKey(KeyInput.newBuilder().setKey(code).setAction(action))
                .build();
    }

    @BeforeEach
    void clear() {
        InputRecorder.reset();
    }

    @Test
    void drainReturnsEveryInputInOrderOnce() {
        List<InputEvent> pressed =
                List.of(key(87, InputAction.INPUT_ACTION_PRESS), key(87, InputAction.INPUT_ACTION_RELEASE));
        pressed.forEach(InputRecorder::record);
        assertEquals(pressed, InputRecorder.drainInputs());
        assertTrue(InputRecorder.drainInputs().isEmpty());
    }

    @Test
    void mouseMovementAddsUpUntilDrained() {
        InputRecorder.addMouseMovement(1.5, -2);
        InputRecorder.addMouseMovement(0.5, 1);
        assertArrayEquals(new double[] {2, -1}, InputRecorder.drainMouseMovement());
        assertArrayEquals(new double[] {0, 0}, InputRecorder.drainMouseMovement());
    }
}
