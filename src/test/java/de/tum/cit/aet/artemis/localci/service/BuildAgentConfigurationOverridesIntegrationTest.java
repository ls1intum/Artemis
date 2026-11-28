package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;

/**
 * The build agent calls the shared {@code BuildAgentConfiguration} spy from scheduled and build threads. Tests therefore must not stub or reset it while the context runs
 * (Mockito keeps the pending stubbing in the mock, so a call from another thread in between makes the stubbing fail or lands on the wrong method). They change what it returns
 * through {@link BuildAgentConfigurationOverrides}, which these tests pin down.
 */
class BuildAgentConfigurationOverridesIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "bacoverrides";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    private static final int NUMBER_OF_STUBBED_METHODS = 3;

    @Test
    void testOverridesAreReturnedAndRemovedAgain() {
        // The base class of the tests configures the Docker client mock for every test.
        assertThat(buildAgentConfiguration.getDockerClient()).isSameAs(dockerClientMock);
        assertThat(buildAgentConfiguration.isDockerAvailable()).isTrue();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        try {
            buildAgentConfigurationOverrides.useBuildExecutor(executor);
            assertThat(buildAgentConfiguration.getBuildExecutor()).isSameAs(executor);

            buildAgentConfigurationOverrides.reset();
            assertThat(buildAgentConfiguration.getDockerClient()).isNotSameAs(dockerClientMock);
            assertThat(buildAgentConfiguration.getBuildExecutor()).isNotSameAs(executor);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testChangingOverridesNeverStubsTheSpyAndNeverDisturbsOtherThreads() throws InterruptedException {
        // The spy is stubbed once while the context is built.
        assertThat(Mockito.mockingDetails(buildAgentConfiguration).getStubbings()).hasSize(NUMBER_OF_STUBBED_METHODS);

        List<Throwable> failuresOnOtherThread = new CopyOnWriteArrayList<>();
        AtomicBoolean stop = new AtomicBoolean(false);
        // Reads the spy in a tight loop, like the scheduled tasks of the build agent do.
        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                try {
                    buildAgentConfiguration.getBuildExecutor();
                    buildAgentConfiguration.getDockerClient();
                    buildAgentConfiguration.isDockerAvailable();
                }
                catch (Throwable failure) {
                    failuresOnOtherThread.add(failure);
                }
            }
        }, "spy-reader");
        reader.start();
        try {
            for (int i = 0; i < 20_000; i++) {
                buildAgentConfigurationOverrides.useDockerClient(dockerClientMock);
                buildAgentConfigurationOverrides.reset();
            }
        }
        finally {
            stop.set(true);
            reader.join(TimeUnit.SECONDS.toMillis(10));
        }

        assertThat(failuresOnOtherThread).isEmpty();
        // Changing overrides did not touch the stubbing of the spy.
        assertThat(Mockito.mockingDetails(buildAgentConfiguration).getStubbings()).hasSize(NUMBER_OF_STUBBED_METHODS);
    }
}
