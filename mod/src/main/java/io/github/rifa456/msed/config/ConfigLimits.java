package io.github.rifa456.msed.config;

import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings each number of a config read from {@code msed.json} into the range the {@code min_*} and
 * {@code max_*} options of {@code config.proto} declare. The settings screen keeps values in range
 * on its own; a hand-edited file is checked here, and each corrected value is logged.
 */
final class ConfigLimits {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigLimits.class);

    private ConfigLimits() {}

    /**
     * Returns the config with every int and float setting inside its declared range. A float that is
     * not finite takes its default.
     *
     * @param config the config as read from the file
     * @param defaults the default config, for floats that are not finite
     * @return the config with every number in range
     */
    static MsedConfig apply(MsedConfig config, MsedConfig defaults) {
        MsedConfig.Builder builder = config.toBuilder();
        clamp(builder, defaults, "");
        return builder.build();
    }

    private static void clamp(Message.Builder builder, Message defaults, String path) {
        for (FieldDescriptor field : builder.getDescriptorForType().getFields()) {
            String name = path + field.getJsonName();
            Object value = builder.getField(field);
            Object fixed = switch (field.getJavaType()) {
                case MESSAGE -> {
                    clamp(builder.getFieldBuilder(field), (Message) defaults.getField(field), name + ".");
                    yield value;
                }
                case INT -> clampInt(field.getOptions(), (Integer) value);
                case FLOAT -> clampFloat(field.getOptions(), (Float) value, (Float) defaults.getField(field));
                default -> value;
            };
            if (!fixed.equals(value)) {
                LOG.warn("msed.json: {} is {}, outside its allowed range; using {}", name, value, fixed);
                builder.setField(field, fixed);
            }
        }
    }

    private static int clampInt(FieldOptions options, int value) {
        int result = value;
        if (options.hasExtension(Options.minInt)) {
            result = Math.max(result, options.getExtension(Options.minInt));
        }
        if (options.hasExtension(Options.maxInt)) {
            result = Math.min(result, options.getExtension(Options.maxInt));
        }
        return result;
    }

    private static float clampFloat(FieldOptions options, float value, float fallback) {
        float result = Float.isFinite(value) ? value : fallback;
        if (options.hasExtension(Options.minFloat)) {
            result = Math.max(result, options.getExtension(Options.minFloat));
        }
        if (options.hasExtension(Options.maxFloat)) {
            result = Math.min(result, options.getExtension(Options.maxFloat));
        }
        return result;
    }
}
