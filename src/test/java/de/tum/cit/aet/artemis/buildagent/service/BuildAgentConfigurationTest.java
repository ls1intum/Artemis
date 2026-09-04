package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.VersionCmd;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

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
    void hostConfig_parsesFractionalCpuLimitsWithoutScalingThemByTen() {
        ProgrammingLanguageConfiguration languageConfiguration = mock(ProgrammingLanguageConfiguration.class);
        when(languageConfiguration.getDefaultDockerFlags()).thenReturn(List.of("--cpus", "0.5", "--memory", "\"2g\"", "--memory-swap", "\"2g\"", "--pids-limit", "1000"));

        var hostConfig = new BuildAgentConfiguration(languageConfiguration).hostConfig();

        assertThat(hostConfig.getCpuPeriod()).isEqualTo(100_000L);
        assertThat(hostConfig.getCpuQuota()).isEqualTo(50_000L);
    }

    @Test
    void pausingBuildJobsKeepsDockerAvailableForGenerationSessions() throws Exception {
        BuildAgentConfiguration configuration = new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class));
        DockerClient dockerClient = mock(DockerClient.class);
        ReflectionTestUtils.setField(configuration, "dockerClient", dockerClient);

        configuration.pauseBuildJobs();

        assertThat(configuration.getDockerClient()).isSameAs(dockerClient);
        verify(dockerClient, never()).close();
    }

    @Test
    void openingBuildAgentServicesReplacesATerminatedExecutor() {
        BuildAgentConfiguration configuration = new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class));
        ThreadPoolExecutor terminatedExecutor = mock(ThreadPoolExecutor.class);
        when(terminatedExecutor.isTerminated()).thenReturn(true);
        DockerClient dockerClient = mock(DockerClient.class);
        VersionCmd versionCmd = mock(VersionCmd.class);
        when(dockerClient.versionCmd()).thenReturn(versionCmd);
        ReflectionTestUtils.setField(configuration, "buildExecutor", terminatedExecutor);
        ReflectionTestUtils.setField(configuration, "dockerClient", dockerClient);
        ReflectionTestUtils.setField(configuration, "specifyConcurrentBuilds", true);
        ReflectionTestUtils.setField(configuration, "concurrentBuildSize", 1);

        configuration.openBuildAgentServices();

        assertThat(configuration.getBuildExecutor()).isNotSameAs(terminatedExecutor);
        configuration.getBuildExecutor().shutdownNow();
    }

    @Test
    void openingBuildAgentServicesRefusesToReplaceAnExecutorThatIsStillStopping() {
        BuildAgentConfiguration configuration = new BuildAgentConfiguration(mock(ProgrammingLanguageConfiguration.class));
        ThreadPoolExecutor stoppingExecutor = mock(ThreadPoolExecutor.class);
        when(stoppingExecutor.isShutdown()).thenReturn(true);
        when(stoppingExecutor.isTerminated()).thenReturn(false);
        DockerClient dockerClient = mock(DockerClient.class);
        when(dockerClient.versionCmd()).thenReturn(mock(VersionCmd.class));
        ReflectionTestUtils.setField(configuration, "buildExecutor", stoppingExecutor);
        ReflectionTestUtils.setField(configuration, "dockerClient", dockerClient);

        assertThatThrownBy(configuration::openBuildAgentServices).isInstanceOf(LocalCIException.class).hasMessageContaining("still stopping");
        assertThat(configuration.getBuildExecutor()).isSameAs(stoppingExecutor);
    }
}
