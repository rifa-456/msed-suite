package io.github.rifa456.msed.episode;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.github.rifa456.msed.proto.v1.EpisodeMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes {@code metadata.json}: the episode manifest, as JSON with the proto field names. */
public final class MetadataFile {
    private MetadataFile() {}

    /**
     * Writes the manifest to {@code metadata.json} in the episode folder, replacing any earlier
     * file. Scalar fields at their default value are written too, so their keys are always present.
     *
     * @param episodeDir the episode folder
     * @param metadata the manifest
     * @throws IOException if the file cannot be written or the manifest cannot be printed as JSON
     */
    public static void write(Path episodeDir, EpisodeMetadata metadata) throws IOException {
        String json;
        try {
            json = JsonFormat.printer()
                    .alwaysPrintFieldsWithNoPresence()
                    .preservingProtoFieldNames()
                    .print(metadata);
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Metadata cannot be printed as JSON", e);
        }
        Files.writeString(episodeDir.resolve(EpisodeFiles.METADATA), json, StandardCharsets.UTF_8);
    }
}
