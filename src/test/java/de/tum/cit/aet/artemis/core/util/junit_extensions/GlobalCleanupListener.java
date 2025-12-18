package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.nio.file.Path;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * GlobalCleanupListener performs a single
 * cleanup operation of local/server-integration-test after all server integration tests have completed.
 *
 * <p>
 * This listener registers with the JUnit Platform Launcher to receive a callback
 * in {@link #testPlanExecutionFinished(TestPlan)} once the entire test plan has
 * executed. Unlike JUnit Jupiter {@link org.junit.jupiter.api.AfterAll} methods and
 * {@link org.junit.jupiter.api.extension.AfterAllCallback} extensions, which are scoped
 * per test container and may run multiple times (once per class), this listener ensures
 * cleanup logic is executed exactly once per test run. Running the cleanup once per class caused issues with parallel test execution.
 *
 * <p>
 * If you want to keep the directory for debugging purposes, you can either comment out the method below or remove the listener from
 * {@code  src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener}.
 */
public class GlobalCleanupListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        RepositoryExportTestUtil.safeDeleteDirectory(Path.of("local", "server-integration-test"));
    }
}
