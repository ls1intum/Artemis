package de.tum.in.www1.artemis.util.junit_extensions;

import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.util.junit_parallel_logging.ParallelConsoleAppender;

/**
 * A JUnit 5 extension that uses {@link ParallelConsoleAppender} to collect logs from parallel test execution.
 * <p>
 * This extension is used to add structural information to the logs, e.g. to indicate the start and end of a test class. At the end of the test class, the collected logs from
 * {@link ParallelConsoleAppender} get printed to the console.
 */
public class ParallelLoggingExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        ParallelConsoleAppender.registerActiveTestGroup(testClass);
        ParallelConsoleAppender.addStringToLogsForGroup("\nStarting logs for " + testClass.getSimpleName() + "\n");

        // Wait until the logger is initialized
        await().until(() -> LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof ch.qos.logback.classic.Logger);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        ParallelConsoleAppender.addStringToLogsForGroup("\n  Starting logs for " + context.getRequiredTestClass().getSimpleName() + " > " + context.getDisplayName() + "\n");
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        ParallelConsoleAppender.addStringToLogsForGroup("\nFinished logs for " + testClass.getSimpleName() + "\n");
        ParallelConsoleAppender.printLogsForGroup(testClass);
        ParallelConsoleAppender.unregisterActiveTestGroup(testClass);
    }

}
