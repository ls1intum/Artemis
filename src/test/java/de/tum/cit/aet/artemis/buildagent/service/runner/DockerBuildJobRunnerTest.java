package de.tum.cit.aet.artemis.buildagent.service.runner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.service.BuildAgentDockerService;
import de.tum.cit.aet.artemis.buildagent.service.BuildJobContainerService;
import de.tum.cit.aet.artemis.buildagent.service.BuildLogsMap;
import de.tum.cit.aet.artemis.localci.exception.DockerImagePullException;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

class DockerBuildJobRunnerTest {

    private BuildJobContainerService buildJobContainerService;

    private BuildAgentDockerService buildAgentDockerService;

    private BuildLogsMap buildLogsMap;

    private DockerBuildJobRunner runner;

    @BeforeEach
    void setUp() {
        buildJobContainerService = mock(BuildJobContainerService.class);
        buildAgentDockerService = mock(BuildAgentDockerService.class);
        buildLogsMap = mock(BuildLogsMap.class);
        runner = new DockerBuildJobRunner(mock(BuildAgentConfiguration.class), buildJobContainerService, buildAgentDockerService, buildLogsMap, "local-ci-");
    }

    @Test
    void classifiesDockerImagePreparationFailures() {
        BuildJobQueueItem buildJob = mock(BuildJobQueueItem.class);
        BuildConfig buildConfig = mock(BuildConfig.class);
        when(buildJob.id()).thenReturn("job-id");
        when(buildJob.buildConfig()).thenReturn(buildConfig);
        when(buildConfig.dockerImage()).thenReturn("image:test");
        doThrow(new LocalCIException("registry unavailable")).when(buildAgentDockerService).pullDockerImage(any(), any());

        assertThatThrownBy(() -> runner.execute(buildJob, null)).isInstanceOf(DockerImagePullException.class).hasMessage("Could not pull Docker image image:test")
                .hasRootCauseMessage("registry unavailable");
        verify(buildLogsMap).appendBuildLogEntry("job-id", "Could not pull Docker image image:test");
    }

    @Test
    void cancellationForceStopsAnActiveContainer() {
        when(buildJobContainerService.getIDOfRunningContainer("local-ci-job-id")).thenReturn("container-id");

        runner.cancel("job-id");

        verify(buildJobContainerService).stopUnresponsiveContainer("container-id");
    }

    @Test
    void cancellationIgnoresAnAlreadyRemovedContainer() {
        runner.cancel("job-id");

        verify(buildJobContainerService, never()).stopUnresponsiveContainer(anyString());
    }
}
