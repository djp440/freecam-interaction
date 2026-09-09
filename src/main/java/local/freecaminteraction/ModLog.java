package local.freecaminteraction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public final class ModLog {
    public static final Logger LOGGER = LogManager.getLogger(FreecamInteractionMod.MOD_ID);

    private ModLog() {}

    public static synchronized void initialize() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        var configuration = context.getConfiguration();
        if (configuration.getAppenders().containsKey(FreecamInteractionMod.MOD_ID)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
        var appender = FileAppender.newBuilder()
                .setName(FreecamInteractionMod.MOD_ID)
                .withFileName("logs/freecam_interaction/" + timestamp + ".log")
                .withAppend(true)
                .setConfiguration(configuration)
                .setLayout(PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n%throwable").build())
                .build();
        if (appender == null) {
            throw new IllegalStateException("无法创建 Mod 日志，请检查运行目录写入权限");
        }
        appender.start();
        configuration.addAppender(appender);
        var logger = new LoggerConfig(FreecamInteractionMod.MOD_ID, Level.DEBUG, true);
        logger.addAppender(appender, Level.DEBUG, null);
        configuration.addLogger(FreecamInteractionMod.MOD_ID, logger);
        context.updateLoggers();
    }
}
