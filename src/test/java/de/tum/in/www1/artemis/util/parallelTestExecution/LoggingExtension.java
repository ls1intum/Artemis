package de.tum.in.www1.artemis.util.parallelTestExecution;

import static org.awaitility.Awaitility.await;

import java.io.File;
import java.time.Duration;

import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit 5 Extension that sets the value of the {@link LoggingDiscriminator} to a combination of the current test class name and test method name.
 * This allows us to write the logs of each test instance to a separate file.
 */
public class LoggingExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(LoggingExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        File logFile = new File(context.getTestClass().orElseThrow().getSimpleName());
        LoggingDiscriminator.setValue(logFile.toString());
        LoggingDiscriminator.setTestGroup(TestGroup.fromClass(context.getRequiredTestClass()));
        LoggingDiscriminator.setThreadId(Thread.currentThread().getId());

        // Wait until the logger is initialized
        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(2)).until(() -> LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        log.info("Starting test: {} > {}", context.getRequiredTestClass().getSimpleName(), context.getDisplayName());
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        LoggingDiscriminator.clear();
    }
}
