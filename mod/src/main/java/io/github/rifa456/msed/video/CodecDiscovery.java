package io.github.rifa456.msed.video;

import io.github.rifa456.msed.proto.v1.FFmpegSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks the FFmpeg encoder the videos are encoded with. Each candidate is tried on a short test
 * clip before it is chosen.
 *
 * <p>The list FFmpeg prints is not proof that an encoder works. Many FFmpeg builds list NVENC on
 * machines without an NVIDIA GPU.
 */
public final class CodecDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(CodecDiscovery.class);

    /**
     * An FFmpeg encoder and the output arguments it is run with.
     *
     * @param codecName the encoder's FFmpeg name, for example {@code libx264}
     * @param arguments the encoder's output arguments, as flag and value pairs
     */
    public record CodecProfile(String codecName, List<String> arguments) {
        @Override
        public @NotNull String toString() {
            return codecName + " " + String.join(" ", arguments);
        }
    }

    /**
     * The encoders tried, in order of preference. Hardware encoders come first because they take
     * the least CPU time from the game. HEVC comes before H.264 for its smaller files.
     */
    private static final List<CodecProfile> CANDIDATES = List.of(
            new CodecProfile("hevc_nvenc", List.of("-preset", "p4", "-rc", "vbr", "-cq", "24")),
            new CodecProfile("hevc_amf", List.of("-quality", "speed", "-rc", "cqp", "-qp_i", "24", "-qp_p", "26")),
            new CodecProfile("hevc_qsv", List.of("-preset", "veryfast", "-global_quality", "24")),
            new CodecProfile("hevc_videotoolbox", List.of("-realtime", "1", "-q:v", "60")),
            new CodecProfile("h264_nvenc", List.of("-preset", "p4", "-rc", "vbr", "-cq", "23")),
            new CodecProfile("h264_amf", List.of("-quality", "speed", "-rc", "cqp", "-qp_i", "22", "-qp_p", "24")),
            new CodecProfile("h264_qsv", List.of("-preset", "veryfast", "-global_quality", "23")),
            new CodecProfile("h264_videotoolbox", List.of("-realtime", "1", "-q:v", "60")),
            new CodecProfile("libx264", List.of("-preset", "ultrafast", "-tune", "zerolatency", "-crf", "23")),
            new CodecProfile("libopenh264", List.of("-b:v", "4M")),
            new CodecProfile("mpeg4", List.of("-q:v", "4")));

    private CodecDiscovery() {}

    /**
     * Returns the first encoder that works with this FFmpeg. With auto-detection off and a codec
     * set in the config, that codec is tried first. Blocks while each candidate is tried, for up
     * to 15 seconds each, so call it off the client thread.
     *
     * @param ffmpeg the FFmpeg executable
     * @param settings the FFmpeg section of the config
     * @return the configured encoder if it works, otherwise the first working candidate
     * @throws IllegalStateException if no candidate works
     */
    public static CodecProfile select(Path ffmpeg, FFmpegSettings settings) {
        if (!settings.getAutoDetectHardwareCodec() && !settings.getVideoCodec().isBlank()) {
            List<String> args = new ArrayList<>();
            if (!settings.getPreset().isBlank()) {
                args.add("-preset");
                args.add(settings.getPreset());
            }
            CodecProfile manual = new CodecProfile(settings.getVideoCodec(), List.copyOf(args));
            if (probe(ffmpeg, manual)) {
                LOG.info("Using configured encoder: {}", manual);
                return manual;
            }
            LOG.warn("Configured encoder {} does not work here; auto-detecting", manual);
        }
        for (CodecProfile candidate : CANDIDATES) {
            if (probe(ffmpeg, candidate)) {
                LOG.info("Selected video encoder: {}", candidate);
                return candidate;
            }
        }
        throw new IllegalStateException("FFmpeg at " + ffmpeg + " cannot encode with any supported codec");
    }

    /**
     * Encodes 4 black frames at 640x360 and discards the output. Gives up after 15 seconds.
     *
     * @param ffmpeg the FFmpeg executable
     * @param profile the encoder to try
     * @return {@code true} if FFmpeg exits with status 0 in time
     */
    static boolean probe(Path ffmpeg, CodecProfile profile) {
        List<String> cmd = new ArrayList<>(List.of(
                ffmpeg.toString(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-nostdin",
                "-f",
                "lavfi",
                "-i",
                "color=c=black:s=640x360:r=20:d=0.2",
                "-c:v",
                profile.codecName()));
        cmd.addAll(profile.arguments());
        cmd.addAll(List.of("-pix_fmt", "yuv420p", "-f", "null", "-"));
        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
