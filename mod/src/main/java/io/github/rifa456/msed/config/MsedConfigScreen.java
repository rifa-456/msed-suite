package io.github.rifa456.msed.config;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import io.github.rifa456.msed.proto.v1.MsedConfig;
import io.github.rifa456.msed.proto.v1.Options;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Cloth Config screen for the MSED settings. Its entries come from the fields of
 * {@link MsedConfig}: each message field is a category or a collapsible group, and each scalar
 * field is an entry with the field's default, bounds and translated tooltip.
 */
public class MsedConfigScreen {
    private static final ConfigEntryBuilder ENTRY = ConfigEntryBuilder.create();

    /**
     * Builds the screen from the config in effect. Saving writes the edited config to the file.
     *
     * @param parent the screen to return to when this one closes, or {@code null} to return to the
     *     game
     * @return the config screen
     * @throws IllegalStateException if the config has a field type the screen cannot edit
     */
    public static Screen create(@Nullable Screen parent) {
        MsedConfig.Builder builder = ConfigFile.getConfig().toBuilder();
        ConfigBuilder screen = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("text.autoconfig.msed.title"));
        screen.setSavingRunnable(() -> ConfigFile.update(builder.build()));

        MsedConfig defaults = ConfigFile.defaults();
        for (Descriptors.FieldDescriptor field : MsedConfig.getDescriptor().getFields()) {
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                String catKey = "text.autoconfig.msed.category." + field.getJsonName();
                ConfigCategory cat = screen.getOrCreateCategory(Component.translatable(catKey));
                buildRecursive(
                        cat,
                        builder.getFieldBuilder(field),
                        (Message) defaults.getField(field),
                        field.getMessageType(),
                        field.getJsonName());
            }
        }
        return screen.build();
    }

    private static void buildRecursive(
            Object parent, Message.Builder builder, Message defaults, Descriptors.Descriptor desc, String path) {
        for (Descriptors.FieldDescriptor field : desc.getFields()) {
            String key = "text.autoconfig.msed.option." + path + "." + field.getJsonName();
            Component name = Component.translatable(key);
            @Nullable Component tooltip = I18n.exists(key + ".tooltip") ? Component.translatable(key + ".tooltip") : null;

            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE
                    && field.getOptions().getExtension(Options.collapsible)) {
                SubCategoryBuilder sub = ENTRY.startSubCategory(name);
                if (tooltip != null) {
                    sub.setTooltip(tooltip);
                }
                buildRecursive(
                        sub,
                        builder.getFieldBuilder(field),
                        (Message) defaults.getField(field),
                        field.getMessageType(),
                        path + "." + field.getJsonName());
                add(parent, sub.build());
            } else {
                add(parent, createEntry(field, builder, defaults.getField(field), name, tooltip));
            }
        }
    }

    private static AbstractConfigListEntry<?> createEntry(
            Descriptors.FieldDescriptor field,
            Message.Builder builder,
            Object defaultValue,
            Component name,
            @Nullable Component tooltip) {
        Component[] tooltips = tooltip != null ? new Component[] {tooltip} : new Component[0];
        var options = field.getOptions();
        return switch (field.getJavaType()) {
            case BOOLEAN ->
                ENTRY.startBooleanToggle(name, (boolean) builder.getField(field))
                        .setTooltip(tooltips)
                        .setDefaultValue((boolean) defaultValue)
                        .setSaveConsumer(v -> builder.setField(field, v))
                        .build();
            case INT -> {
                int min = options.getExtension(Options.minInt);
                int max = options.getExtension(Options.maxInt);
                if (options.hasExtension(Options.maxInt)) {
                    yield ENTRY.startIntSlider(name, (int) builder.getField(field), min, max)
                            .setTooltip(tooltips)
                            .setDefaultValue((int) defaultValue)
                            .setSaveConsumer(v -> builder.setField(field, v))
                            .build();
                }
                yield ENTRY.startIntField(name, (int) builder.getField(field))
                        .setTooltip(tooltips)
                        .setDefaultValue((int) defaultValue)
                        .setMin(options.hasExtension(Options.minInt) ? min : Integer.MIN_VALUE)
                        .setSaveConsumer(v -> builder.setField(field, v))
                        .build();
            }
            case FLOAT -> {
                float min = options.hasExtension(Options.minFloat)
                        ? options.getExtension(Options.minFloat)
                        : -Float.MAX_VALUE;
                float max = options.hasExtension(Options.maxFloat)
                        ? options.getExtension(Options.maxFloat)
                        : Float.MAX_VALUE;
                yield ENTRY.startFloatField(name, (float) builder.getField(field))
                        .setTooltip(tooltips)
                        .setDefaultValue((float) defaultValue)
                        .setMin(min)
                        .setMax(max)
                        .setSaveConsumer(v -> builder.setField(field, v))
                        .build();
            }
            case STRING ->
                ENTRY.startStrField(name, (String) builder.getField(field))
                        .setTooltip(tooltips)
                        .setDefaultValue((String) defaultValue)
                        .setSaveConsumer(v -> builder.setField(field, v))
                        .build();
            default -> throw new IllegalStateException("No config editor for field " + field.getFullName());
        };
    }

    private static void add(Object parent, AbstractConfigListEntry<?> entry) {
        if (parent instanceof ConfigCategory category) {
            category.addEntry(entry);
        } else if (parent instanceof SubCategoryBuilder sub) {
            sub.add(entry);
        }
    }
}
