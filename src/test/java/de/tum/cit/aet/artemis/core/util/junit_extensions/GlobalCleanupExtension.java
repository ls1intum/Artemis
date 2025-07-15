package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class GlobalCleanupExtension implements TestExecutionListener {

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            FileUtils.deleteDirectory(Path.of("local", "server-integration-test").toFile());
        }
        catch (IOException ignored) {
            // ignore failure
        }
    }
}
