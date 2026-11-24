package io.github.rifa456.msed.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.Options;
import org.junit.jupiter.api.Test;

class ConfigDefaultsTest {
    private final MsedConfig defaults = ConfigDefaults.build();

    @Test
    void schemaDefaultsAreApplied() {
        assertEquals("dataset", defaults.getFilesystem().getDatasetRoot());
        assertEquals(1000, defaults.getAdvanced().getTicksPerShard());
        assertEquals(64.0f, defaults.getVision().getMaxRayDistance());
        assertTrue(defaults.getFfmpeg().getAutoDetectHardwareCodec());
        assertEquals("", defaults.getFfmpeg().getExecutable());
    }

    @Test
    void everyModalityIsRecordedByDefault() {
        assertAllTrue(defaults.getRecording());
    }

    @Test
    void everyGroupIsPresentAndEveryDefaultWithinItsLimits() {
        assertWithinLimits(defaults);
    }

    private static void assertAllTrue(Message message) {
        for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
            Object value = message.getField(field);
            if (value instanceof Message nested) {
                assertAllTrue(nested);
            } else {
                assertEquals(true, value, field.getFullName());
            }
        }
    }

    private static void assertWithinLimits(Message message) {
        for (FieldDescriptor field : message.getDescriptorForType().getFields()) {
            Object value = message.getField(field);
            FieldOptions options = field.getOptions();
            if (value instanceof Message nested) {
                assertTrue(message.hasField(field), field.getFullName());
                assertWithinLimits(nested);
            } else if (value instanceof Integer number) {
                if (options.hasExtension(Options.minInt)) {
                    assertTrue(number >= options.getExtension(Options.minInt), field.getFullName());
                }
                if (options.hasExtension(Options.maxInt)) {
                    assertTrue(number <= options.getExtension(Options.maxInt), field.getFullName());
                }
            } else if (value instanceof Float number) {
                if (options.hasExtension(Options.minFloat)) {
                    assertTrue(number >= options.getExtension(Options.minFloat), field.getFullName());
                }
                if (options.hasExtension(Options.maxFloat)) {
                    assertTrue(number <= options.getExtension(Options.maxFloat), field.getFullName());
                }
            }
        }
    }
}
