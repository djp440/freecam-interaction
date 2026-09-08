package local.godviewbuild;

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
    public static final Logger LOGGER = LogManager.getLogger(GodviewBuild.MOD_ID);

    private ModLog() {}

    public static synchronized void initialize() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        var configuration = context.getConfiguration();
        if (configuration.getAppenders().containsKey(GodviewBuild.MOD_ID)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"));
        var appender = FileAppender.newBuilder()
                .setName(GodviewBuild.MOD_ID)
                .withFileName("logs/godview_build/" + timestamp + ".log")
                .withAppend(true)
                .setConfiguration(configuration)
                .setLayout(PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %msg%n%throwable").build())
                .build();
        if (appender == null) {
            throw new IllegalStateException("无法创建 Mod 日志，请检查运行目录写入权限");
        }
        appender.start();
        configuration.addAppender(appender);
        var logger = new LoggerConfig(GodviewBuild.MOD_ID, Level.DEBUG, true);
        logger.addAppender(appender, Level.DEBUG, null);
        configuration.addLogger(GodviewBuild.MOD_ID, logger);
        context.updateLoggers();
    }
}
