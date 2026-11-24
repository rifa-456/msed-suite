package io.github.rifa456.msed.episode;

import com.google.protobuf.ByteString;
import io.github.rifa456.msed.proto.v1.ShardInfo;
import io.github.rifa456.msed.proto.v1.TickRecord;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes one shard: length-delimited tick records, hashed with SHA-256 as they are written. */
public final class ShardWriter implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ShardWriter.class);
    /** Read size when a shard is hashed from disk. */
    private static final int HASH_CHUNK_BYTES = 1 << 20;

    private final Path path;
    private final MessageDigest digest;
    private final DigestOutputStream out;
    private long firstTick = -1;
    private long lastTick = -1;
    private int tickCount;

    /**
     * Creates the shard file, replacing any file at that path.
     *
     * @param path the shard file
     * @throws IOException if the file cannot be created
     */
    public ShardWriter(Path path) throws IOException {
        this.path = path;
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime provides SHA-256", e);
        }
        this.out = new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(path)), digest);
    }

    /**
     * Appends one tick record, prefixed with its length as a varint.
     *
     * @param record the tick record
     * @throws IOException if the record cannot be written
     */
    public void write(TickRecord record) throws IOException {
        long tick = record.getData().getTick();
        if (tickCount == 0) {
            firstTick = tick;
        }
        lastTick = tick;
        record.writeDelimitedTo(out);
        tickCount++;
    }

    /**
     * Returns the number of tick records written to this shard.
     *
     * @return the record count
     */
    public int getTickCount() {
        return tickCount;
    }

    /**
     * Closes the file and describes it for the episode manifest. Call it once, after the last
     * {@link #write}. When closing fails, for example on a full disk, the shard is described as
     * it is on disk, so the toolkit finds the records it cut short.
     *
     * @return the shard's file name, first and last tick, size and SHA-256 digest; both ticks are
     *     -1 if no record was written
     * @throws IOException if the file cannot be closed or its size read
     */
    public ShardInfo finish() throws IOException {
        byte[] sha256;
        try {
            out.close();
            sha256 = digest.digest();
        } catch (IOException e) {
            // The digest covers bytes that never reached the disk, so hash the file as it is.
            LOG.error("Could not finish shard {}; it is listed as it is on disk", path.getFileName(), e);
            sha256 = sha256OnDisk();
        }
        return ShardInfo.newBuilder()
                .setFile(path.getFileName().toString())
                .setFirstTick(firstTick)
                .setLastTick(lastTick)
                .setSizeBytes(Files.size(path))
                .setSha256(ByteString.copyFrom(sha256))
                .build();
    }

    private byte[] sha256OnDisk() throws IOException {
        digest.reset();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] chunk = new byte[HASH_CHUNK_BYTES];
            for (int read = in.read(chunk); read != -1; read = in.read(chunk)) {
                digest.update(chunk, 0, read);
            }
        }
        return digest.digest();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
