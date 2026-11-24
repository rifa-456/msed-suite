package io.github.rifa456.msed.vocabulary;

import com.google.protobuf.Descriptors;
import com.google.protobuf.util.JsonFormat;
import io.github.rifa456.msed.episode.EpisodeFiles;
import io.github.rifa456.msed.proto.v1.EpisodeVocabulary;
import io.github.rifa456.msed.proto.v1.Options;
import io.github.rifa456.msed.proto.v1.RegistryMap;
import io.github.rifa456.msed.proto.v1.TickData;
import io.github.rifa456.msed.proto.v1.Vocab;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes {@code vocabulary.json}, the map from the integer ids in an episode's tick records to
 * names. It lists every entry of the block, item, entity type, sound and effect registries, the
 * screens, tags and events hashed during the session, and which tick record fields hold which kind
 * of id.
 */
public class VocabularyWriter {
    private static final Logger LOG = LoggerFactory.getLogger(VocabularyWriter.class);

    /**
     * Writes the vocabulary of the session that has ended to the episode folder. Call before the
     * next session starts, which clears the hashed names. An I/O error is logged, not thrown.
     *
     * @param episodePath the episode folder
     */
    public static void write(Path episodePath) {
        Path vocabPath = episodePath.resolve(EpisodeFiles.VOCABULARY);
        EpisodeVocabulary.Builder builder = EpisodeVocabulary.newBuilder();
        builder.setBlocks(buildRegistryMap(BuiltInRegistries.BLOCK));
        builder.setItems(buildRegistryMap(BuiltInRegistries.ITEM));
        builder.setEntities(buildRegistryMap(BuiltInRegistries.ENTITY_TYPE));
        builder.setSounds(buildRegistryMap(BuiltInRegistries.SOUND_EVENT));
        builder.setEffects(buildRegistryMap(BuiltInRegistries.MOB_EFFECT));
        Vocabulary service = Vocabulary.getInstance();
        builder.setScreens(buildMap(service.getSeenScreens()));
        builder.setSemanticTags(buildMap(service.getSeenTags()));
        builder.setEvents(buildMap(service.getSeenEvents()));
        Map<String, Vocab> fieldIndex = new HashMap<>();
        scanSchema(TickData.getDescriptor(), "tick", fieldIndex);
        builder.putAllFieldIndex(fieldIndex);
        EpisodeVocabulary vocab = builder.build();
        try {
            String json = JsonFormat.printer()
                    .alwaysPrintFieldsWithNoPresence()
                    .preservingProtoFieldNames()
                    .print(vocab);
            Files.writeString(vocabPath, json, StandardCharsets.UTF_8);
            LOG.info("Vocabulary saved");
        } catch (IOException e) {
            LOG.error("Failed to write vocabulary", e);
        }
    }

    /**
     * Adds every field under {@code descriptor} marked with a vocabulary option to {@code index},
     * keyed by its dotted path from {@code tick}.
     */
    private static void scanSchema(Descriptors.Descriptor descriptor, String currentPath, Map<String, Vocab> index) {
        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            String path = currentPath + "." + field.getName();
            if (field.getOptions().hasExtension(Options.vocab)) {
                Vocab type = field.getOptions().getExtension(Options.vocab);
                if (type != Vocab.VOCAB_UNKNOWN) {
                    index.put(path, type);
                }
            }
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                scanSchema(field.getMessageType(), path, index);
            }
        }
    }

    private static <T> RegistryMap buildRegistryMap(Registry<T> registry) {
        RegistryMap.Builder mapBuilder = RegistryMap.newBuilder();
        for (T entry : registry) {
            int id = registry.getId(entry);
            String name = Objects.requireNonNull(registry.getKey(entry)).toString();
            mapBuilder.putEntries(id, name);
        }
        return mapBuilder.build();
    }

    private static RegistryMap buildMap(Map<Integer, String> map) {
        return RegistryMap.newBuilder().putAllEntries(map).build();
    }
}
