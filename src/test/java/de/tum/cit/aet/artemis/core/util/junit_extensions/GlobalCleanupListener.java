package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.nio.file.Path;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * GlobalCleanupListener performs setup that has to happen exactly once before all server integration tests, and a single
 * cleanup operation of local server-integration-test directories after all server integration tests have completed.
 *
 * <p>
 * This listener registers with the JUnit Platform Launcher to receive callbacks in {@link #testPlanExecutionStarted(TestPlan)} and
 * in {@link #testPlanExecutionFinished(TestPlan)}, i.e. before and after the entire test plan has
 * executed. Unlike JUnit Jupiter {@link org.junit.jupiter.api.BeforeAll} / {@link org.junit.jupiter.api.AfterAll} methods and
 * {@link org.junit.jupiter.api.extension.AfterAllCallback} extensions, which are scoped
 * per test container and may run multiple times (once per class), this listener ensures
 * the logic is executed exactly once per test run, while the JVM is still single-threaded. Running it once per class caused issues with parallel test execution,
 * both for the cleanup and for the JGit {@link JGitSystemReaderInitializer configuration}.
 *
 * <p>
 * If you want to keep the directory for debugging purposes, comment out {@link #testPlanExecutionFinished(TestPlan)}. Do not remove the listener from
 * {@code src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener}, because the JGit setup would be lost as well.
 */
public class GlobalCleanupListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        // Must happen here and not in a @BeforeAll: installing a JGit SystemReader resets JGit's static platform detection caches,
        // which makes git operations of already running test classes fail. See JGitSystemReaderInitializer.
        JGitSystemReaderInitializer.configureOnce();
        CpuUsageMonitor.start();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        TestBucketTiming.recordExecutionStarted(testIdentifier);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        TestBucketTiming.recordExecutionFinished(testIdentifier);
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        TestBucketTiming.recordExecutionSkipped(testIdentifier);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        TestBucketTiming.printSummary();
        RepositoryExportTestUtil.safeDeleteDirectory(Path.of("local", "server-integration-test"));
        RepositoryExportTestUtil.safeDeleteDirectory(Path.of("local", "server-integration-test-independent-batch"));
    }
}
