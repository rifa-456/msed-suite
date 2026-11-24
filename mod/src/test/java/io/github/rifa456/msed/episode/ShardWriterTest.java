package io.github.rifa456.msed.episode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.rifa456.msed.proto.v1.ShardInfo;
import io.github.rifa456.msed.proto.v1.TickData;
import io.github.rifa456.msed.proto.v1.TickRecord;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShardWriterTest {
    @Test
    void describesTheRecordsItWrote(@TempDir Path folder) throws IOException, NoSuchAlgorithmException {
        Path path = folder.resolve(EpisodeFiles.shard(3));
        ShardInfo info;
        try (ShardWriter writer = new ShardWriter(path)) {
            for (long tick = 10; tick <= 12; tick++) {
                writer.write(TickRecord.newBuilder()
                        .setData(TickData.newBuilder().setTick(tick))
                        .build());
            }
            info = writer.finish();
        }
        byte[] bytes = Files.readAllBytes(path);
        assertEquals("shard_0003.bin", info.getFile());
        assertEquals(10, info.getFirstTick());
        assertEquals(12, info.getLastTick());
        assertEquals(bytes.length, info.getSizeBytes());
        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(bytes),
                info.getSha256().toByteArray());
        try (InputStream in = Files.newInputStream(path)) {
            for (long tick = 10; tick <= 12; tick++) {
                assertEquals(tick, TickRecord.parseDelimitedFrom(in).getData().getTick());
            }
            assertNull(TickRecord.parseDelimitedFrom(in));
        }
    }
}
