package de.tum.cit.aet.artemis.core.config.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import de.tum.cit.aet.artemis.core.config.ArtemisProperties;

/**
 * Inlined replacement for JHipster's {@code LoggingUtils}.
 * <p>
 * Provides methods that configure Logback appenders for JSON console output
 * and Logstash TCP socket forwarding. Since the {@code logstash-logback-encoder}
 * dependency is excluded, the Logstash TCP appender is a no-op stub that logs
 * a warning, while the JSON console appender uses a basic pattern layout.
 */
public final class LoggingUtils {

    private static final Logger log = LoggerFactory.getLogger(LoggingUtils.class);

    private static final String CONSOLE_APPENDER_NAME = "CONSOLE";

    private static final String ASYNC_LOGSTASH_APPENDER_NAME = "ASYNC_LOGSTASH";

    private LoggingUtils() {
        // utility class
    }

    /**
     * Adds (or replaces) the console appender with a JSON-formatted variant.
     * <p>
     * Without the logstash-logback-encoder library on the classpath a structured
     * pattern layout is used instead of a true JSON encoder.
     *
     * @param context      the Logback {@link LoggerContext}
     * @param customFields JSON string of additional fields (currently informational)
     */
    public static void addJsonConsoleAppender(LoggerContext context, String customFields) {
        log.info("Initializing JSON Console logging with custom fields: {}", customFields);

        // Remove existing console appender
        var rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        var existingAppender = rootLogger.getAppender(CONSOLE_APPENDER_NAME);
        if (existingAppender != null) {
            rootLogger.detachAppender(existingAppender);
        }

        // Create a simple structured console appender
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName(CONSOLE_APPENDER_NAME);

        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("{\"timestamp\":\"%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}\",\"level\":\"%level\",\"logger\":\"%logger\",\"thread\":\"%thread\",\"message\":\"%msg\"}%n");
        layout.start();

        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(layout);
        encoder.start();

        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        rootLogger.addAppender(consoleAppender);
    }

    /**
     * Adds a TCP socket appender for forwarding logs to a Logstash instance.
     * <p>
     * Without the logstash-logback-encoder library this is a no-op that emits a
     * warning so operators know the feature is unavailable.
     *
     * @param context            the Logback {@link LoggerContext}
     * @param customFields       JSON string of additional fields
     * @param logstashProperties the Logstash connection properties
     */
    public static void addLogstashTcpSocketAppender(LoggerContext context, String customFields, ArtemisProperties.Logging.Logstash logstashProperties) {
        log.warn("Logstash TCP appender requested (host={}, port={}) but logstash-logback-encoder is not on the classpath. "
                + "Add net.logstash.logback:logstash-logback-encoder to enable this feature.", logstashProperties.getHost(), logstashProperties.getPort());
    }

    /**
     * Adds a {@link LoggerContextListener} that re-applies JSON / Logstash
     * configuration after a context reset (e.g. triggered by Spring's logging
     * system re-initialisation).
     *
     * @param context           the Logback {@link LoggerContext}
     * @param customFields      JSON string of additional fields
     * @param loggingProperties the logging properties
     */
    public static void addContextListener(LoggerContext context, String customFields, ArtemisProperties.Logging loggingProperties) {
        var listener = new LogbackReconfigureListener(customFields, loggingProperties);
        context.addListener(listener);
    }

    /**
     * A {@link LoggerContextListener} that re-applies the JSON / Logstash
     * configuration after a Logback context reset.
     */
    private static class LogbackReconfigureListener implements LoggerContextListener {

        private final String customFields;

        private final ArtemisProperties.Logging loggingProperties;

        LogbackReconfigureListener(String customFields, ArtemisProperties.Logging loggingProperties) {
            this.customFields = customFields;
            this.loggingProperties = loggingProperties;
        }

        @Override
        public boolean isResetResistant() {
            return true;
        }

        @Override
        public void onStart(LoggerContext context) {
            // no-op
        }

        @Override
        public void onReset(LoggerContext context) {
            if (loggingProperties.isUseJsonFormat()) {
                addJsonConsoleAppender(context, customFields);
            }
            if (loggingProperties.getLogstash().isEnabled()) {
                addLogstashTcpSocketAppender(context, customFields, loggingProperties.getLogstash());
            }
        }

        @Override
        public void onStop(LoggerContext context) {
            // no-op
        }

        @Override
        public void onLevelChange(ch.qos.logback.classic.Logger logger, ch.qos.logback.classic.Level level) {
            // no-op
        }
    }
}
