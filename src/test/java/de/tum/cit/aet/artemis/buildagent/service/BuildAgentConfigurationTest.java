package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;

class BuildAgentConfigurationTest {

    @Test
    void testBytes() {
        assertThat("512 Bytes").isEqualTo(BuildAgentConfiguration.formatMemory(512));
    }

    @Test
    void testKilobytes() {
        assertThat("1 KB").isEqualTo(BuildAgentConfiguration.formatMemory(1024));
        assertThat("999 KB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 999));
    }

    @Test
    void testMegabytes() {
        assertThat("1 MB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024));
        assertThat("1023 MB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1023L));
    }

    @Test
    void testGigabytes() {
        assertThat("1.0 GB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1024L));
        assertThat("1.5 GB").isEqualTo(BuildAgentConfiguration.formatMemory(1024 * 1024 * 1024 * 3L / 2));
    }

    @Test
    void shouldUseDedicatedExecutorForBuildResultWaits() throws Exception {
        var configuration = new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class));
        ReflectionTestUtils.setField(configuration, "specifyConcurrentBuilds", true);
        ReflectionTestUtils.setField(configuration, "concurrentBuildSize", 1);
        ReflectionTestUtils.setField(configuration, "buildRunner", "kubernetes");
        configuration.onApplicationReady();

        try {
            assertThat(configuration.getBuildResultExecutor()).isNotSameAs(configuration.getBuildExecutor());
            assertThat(configuration.getBuildResultExecutor().submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS)).startsWith("local-ci-build-result-");
        }
        finally {
            configuration.closeBuildAgentServices();
        }

        assertThat(configuration.getBuildExecutor()).isNull();
        assertThat(configuration.getBuildResultExecutor()).isNull();
    }

    @Test
    void shouldQueueOneReplacementWaiterPerConcurrentBuild() throws Exception {
        int concurrentBuilds = 3;
        var configuration = new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class));
        ReflectionTestUtils.setField(configuration, "specifyConcurrentBuilds", true);
        ReflectionTestUtils.setField(configuration, "concurrentBuildSize", concurrentBuilds);
        ReflectionTestUtils.setField(configuration, "buildRunner", "kubernetes");
        configuration.onApplicationReady();

        CountDownLatch activeWaitersStarted = new CountDownLatch(concurrentBuilds);
        CountDownLatch releaseActiveWaiters = new CountDownLatch(1);
        try {
            for (int i = 0; i < concurrentBuilds; i++) {
                configuration.getBuildResultExecutor().submit(() -> {
                    activeWaitersStarted.countDown();
                    releaseActiveWaiters.await();
                    return null;
                });
            }
            assertThat(activeWaitersStarted.await(5, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < concurrentBuilds; i++) {
                configuration.getBuildResultExecutor().submit(() -> null);
            }
            assertThat(configuration.getBuildResultExecutor().getQueue()).hasSize(concurrentBuilds);
        }
        finally {
            releaseActiveWaiters.countDown();
            configuration.closeBuildAgentServices();
        }
    }
}
