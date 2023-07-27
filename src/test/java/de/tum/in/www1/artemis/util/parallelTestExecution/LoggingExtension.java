package de.tum.in.www1.artemis.util.parallelTestExecution;

import static org.awaitility.Awaitility.await;

import java.io.File;
import java.time.Duration;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 Extension that sets the value of the {@link LoggingDiscriminator} to a combination of the current test class name and test method name.
 * This allows us to write the logs of each test instance to a separate file.
 */
public class LoggingExtension implements BeforeEachCallback, BeforeAllCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        File logFile = new File(context.getTestClass().orElseThrow().getSimpleName(), context.getDisplayName().replace(File.separatorChar, ':'));
        LoggingDiscriminator.setValue(logFile.toString());
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // Wait until the logger is initialized
        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(2)).until(() -> LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger);
    }
}
