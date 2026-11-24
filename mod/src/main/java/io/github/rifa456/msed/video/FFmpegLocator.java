package io.github.rifa456.msed.video;

import com.google.common.base.Splitter;
import io.github.rifa456.msed.config.ConfigFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds a working FFmpeg executable on this machine. MSED does not ship FFmpeg. Video recording
 * uses the player's own installation, and is off when none is found. The player then gets the
 * install command in chat.
 */
public final class FFmpegLocator {
    private static final Logger LOG = LoggerFactory.getLogger(FFmpegLocator.class);

    /** The folder where Homebrew on Intel Macs and source builds on Linux install programs. */
    private static final Path USR_LOCAL_BIN = Path.of("/usr/local/bin");

    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");
    private static final String BINARY = WINDOWS ? "ffmpeg.exe" : "ffmpeg";

    private FFmpegLocator() {}

    /**
     * Returns the command that installs FFmpeg on this operating system, for the message shown
     * when FFmpeg is missing.
     *
     * <p>On Windows, this is a {@code winget} command. On macOS, it is a Homebrew command. On other
     * systems, it is an {@code apt} command with a note about other package managers.
     *
     * @return the install command, on one line
     */
    public static String installHint() {
        if (WINDOWS) return "winget install ffmpeg";
        if (MAC) return "brew install ffmpeg";
        return "sudo apt install ffmpeg (or your distribution's package manager)";
    }

    /**
     * Returns the first FFmpeg executable that runs. The path set in the config is tried first,
     * then every folder on {@code PATH}, then the usual install folders of this operating system.
     * Each candidate is run with {@code -version}, for up to 5 seconds, so call it off the client
     * thread.
     *
     * <p>On Windows, the extra folders are those of winget, Scoop and Chocolatey, and
     * {@code C:\ffmpeg\bin}. On macOS, they are the Homebrew folders. On Linux, they are
     * {@code /usr/bin}, {@code /usr/local/bin} and {@code /snap/bin}.
     *
     * @return the executable, or empty if none runs
     */
    public static Optional<Path> find() {
        String configured = ConfigFile.getConfig().getFfmpeg().getExecutable().trim();
        if (!configured.isEmpty()) {
            Optional<Path> path = parse(configured);
            if (path.isPresent() && works(path.get())) return path;
            LOG.warn("Configured FFmpeg executable does not run: {}; searching for another", configured);
        }
        for (Path candidate : candidates()) {
            if (Files.isRegularFile(candidate) && works(candidate)) {
                LOG.info("Using FFmpeg at {}", candidate);
                return Optional.of(candidate);
            }
        }
        LOG.warn("FFmpeg not found, so video recording is off. Install it with: {}", installHint());
        return Optional.empty();
    }

    private static Optional<Path> parse(String text) {
        try {
            return Optional.of(Path.of(text));
        } catch (InvalidPathException e) {
            LOG.warn("Skipping {}, which is not a valid path: {}", text, e.getMessage());
            return Optional.empty();
        }
    }

    private static List<Path> candidates() {
        List<Path> dirs = new ArrayList<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : Splitter.on(File.pathSeparatorChar)
                    .omitEmptyStrings()
                    .trimResults()
                    .split(pathEnv)) {
                parse(dir).ifPresent(dirs::add);
            }
        }
        String home = System.getProperty("user.home");
        if (WINDOWS) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) dirs.add(Paths.get(localAppData, "Microsoft", "WinGet", "Links"));
            dirs.add(Paths.get(home, "scoop", "shims"));
            dirs.add(Paths.get("C:", "ProgramData", "chocolatey", "bin"));
            dirs.add(Paths.get("C:", "ffmpeg", "bin"));
        } else if (MAC) {
            dirs.add(Paths.get("/opt/homebrew/bin"));
            dirs.add(USR_LOCAL_BIN);
        } else {
            dirs.add(Paths.get("/usr/bin"));
            dirs.add(USR_LOCAL_BIN);
            dirs.add(Paths.get("/snap/bin"));
        }
        return dirs.stream().map(d -> d.resolve(BINARY)).distinct().toList();
    }

    private static boolean works(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "-hide_banner", "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
