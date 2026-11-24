package io.github.rifa456.msed.config;

import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.Options;

/**
 * Builds the default config from the {@code default_*} options on the fields of {@code
 * config.proto}, so the schema is the only place a default is written. A field without such an
 * option keeps its protobuf default: 0, false or empty.
 */
final class ConfigDefaults {
    private ConfigDefaults() {}

    /**
     * Returns a config with every setting at its default and every settings group present.
     *
     * @return a new default config
     */
    static MsedConfig build() {
        MsedConfig.Builder builder = MsedConfig.newBuilder();
        fill(builder);
        return builder.build();
    }

    private static void fill(Message.Builder builder) {
        for (FieldDescriptor field : builder.getDescriptorForType().getFields()) {
            FieldOptions options = field.getOptions();
            if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                fill(builder.getFieldBuilder(field));
            } else if (options.hasExtension(Options.defaultInt)) {
                builder.setField(field, options.getExtension(Options.defaultInt));
            } else if (options.hasExtension(Options.defaultFloat)) {
                builder.setField(field, options.getExtension(Options.defaultFloat));
            } else if (options.hasExtension(Options.defaultBool)) {
                builder.setField(field, options.getExtension(Options.defaultBool));
            } else if (options.hasExtension(Options.defaultString)) {
                builder.setField(field, options.getExtension(Options.defaultString));
            }
        }
    }
}
