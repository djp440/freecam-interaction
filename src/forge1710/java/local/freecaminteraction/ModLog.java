package local.freecaminteraction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public final class ModLog {
    private static final Logger LOGGER = Logger.getLogger("freecam_interaction");
    private static StreamHandler file;

    private ModLog() {}

    public static synchronized void initialize() {
        if (file != null) return;
        try {
            Path directory = Paths.get("logs", "freecam_interaction");
            Files.createDirectories(directory);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
            file = new StreamHandler(Files.newOutputStream(directory.resolve(stamp + ".log"),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), new SimpleFormatter());
            file.setEncoding(StandardCharsets.UTF_8.name());
            file.setLevel(Level.ALL);
            LOGGER.setUseParentHandlers(false);
            LOGGER.setLevel(Level.ALL);
            LOGGER.addHandler(file);
            LOGGER.addHandler(new ConsoleHandler());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> file.close(), "freecam-log-close"));
        } catch (IOException error) {
            throw new IllegalStateException("无法创建 Mod 日志", error);
        }
    }

    public static synchronized void info(String message) {
        LOGGER.info(message);
        if (file != null) file.flush();
    }
}
