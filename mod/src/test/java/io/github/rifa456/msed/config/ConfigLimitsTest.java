package io.github.rifa456.msed.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Descriptors.Descriptor;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.Options;
import io.github.rifa456.msed.proto.v1.RecordingBehavior;
import io.github.rifa456.msed.proto.v1.VisionSettings;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ConfigLimitsTest {
    private final MsedConfig defaults = ConfigDefaults.build();

    private static int minInt(Descriptor message, String field) {
        return message.findFieldByName(field).getOptions().getExtension(Options.minInt);
    }

    private static int maxInt(Descriptor message, String field) {
        return message.findFieldByName(field).getOptions().getExtension(Options.maxInt);
    }

    private MsedConfig withVision(Consumer<VisionSettings.Builder> edit) {
        MsedConfig.Builder builder = defaults.toBuilder();
        edit.accept(builder.getVisionBuilder());
        return builder.build();
    }

    @Test
    void valuesBelowTheMinimumAreRaisedToIt() {
        MsedConfig config = withVision(v -> v.setRgbWidth(0).setNonTransparentRayGridWidth(-3));
        MsedConfig fixed = ConfigLimits.apply(config, defaults);
        Descriptor vision = VisionSettings.getDescriptor();
        assertEquals(minInt(vision, "rgb_width"), fixed.getVision().getRgbWidth());
        assertEquals(
                minInt(vision, "non_transparent_ray_grid_width"),
                fixed.getVision().getNonTransparentRayGridWidth());
    }

    @Test
    void valuesAboveTheMaximumAreLoweredToIt() {
        MsedConfig.Builder builder = defaults.toBuilder();
        builder.getRecordingBehaviorBuilder().setGracePeriodSeconds(Integer.MAX_VALUE);
        MsedConfig fixed = ConfigLimits.apply(builder.build(), defaults);
        assertEquals(
                maxInt(RecordingBehavior.getDescriptor(), "grace_period_seconds"),
                fixed.getRecordingBehavior().getGracePeriodSeconds());
    }

    @Test
    void nonFiniteFloatsTakeTheirDefault() {
        MsedConfig config =
                withVision(v -> v.setMaxRayDistance(Float.NaN).setFoveationExponent(Float.NEGATIVE_INFINITY));
        MsedConfig fixed = ConfigLimits.apply(config, defaults);
        assertEquals(defaults.getVision().getMaxRayDistance(), fixed.getVision().getMaxRayDistance());
        assertEquals(
                defaults.getVision().getFoveationExponent(), fixed.getVision().getFoveationExponent());
    }

    @Test
    void valuesInRangeAreKept() {
        assertEquals(defaults, ConfigLimits.apply(defaults, defaults));
    }
}
