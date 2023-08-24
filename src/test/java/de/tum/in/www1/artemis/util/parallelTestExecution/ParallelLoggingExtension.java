package de.tum.in.www1.artemis.util.parallelTestExecution;

import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParallelLoggingExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        ParallelConsoleAppender.registerActiveTestGroup(testClass);
        ParallelConsoleAppender.addStringToLogsForGroup("\nStarting logs for " + testClass.getSimpleName() + "\n");

        // Wait until the logger is initialized
        await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(2)).until(() -> LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        ParallelConsoleAppender.addStringToLogsForGroup("\n  Starting logs for " + context.getRequiredTestClass().getSimpleName() + " > " + context.getDisplayName() + "\n");
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        ParallelConsoleAppender.addStringToLogsForGroup("\nFinished logs for " + testClass.getSimpleName() + "\n");
        ParallelConsoleAppender.printLogsForGroup(testClass);
        ParallelConsoleAppender.unregisterActiveTestGroup(testClass);
    }

}
