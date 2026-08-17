package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;

/**
 * Verifies that the build timeout does not cancel a job while its Docker image is still being pulled.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildJobManagementServiceTest {

    private static final String BUILD_JOB_ID = "build-job-1";

    /**
     * Kept well below the pull duration so that a build budget exhausted by the pull would fail the test.
     */
    private static final int BUILD_TIMEOUT_SECONDS = 2;

    @Mock
    private BuildAgentDockerService buildAgentDockerService;

    private BuildJobManagementService buildJobManagementService;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void setUpService() {
        executor = Executors.newSingleThreadExecutor();
        buildJobManagementService = new BuildJobManagementService(null, null, null, null, null, null, buildAgentDockerService);
    }

    @Test
    void awaitBuildResult_doesNotTimeOutWhileTheImageIsStillBeingPulled() throws Exception {
        setUpService();

        // The pull takes longer than the whole build budget, but only ~200 ms of the wait happens outside the pull.
        long pullEndNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        when(buildAgentDockerService.isImagePullInProgress(BUILD_JOB_ID)).thenAnswer(invocation -> System.nanoTime() < pullEndNanos);

        BuildResult expectedResult = someBuildResult();
        Future<BuildResult> future = executor.submit(() -> {
            Thread.sleep(3200);
            return expectedResult;
        });

        BuildResult result = buildJobManagementService.awaitBuildResult(future, BUILD_JOB_ID, BUILD_TIMEOUT_SECONDS);

        assertThat(result).isSameAs(expectedResult);
        assertThat(future).isNotCancelled();
    }

    @Test
    void awaitBuildResult_timesOutWhenNoImagePullIsInProgress() {
        setUpService();

        when(buildAgentDockerService.isImagePullInProgress(BUILD_JOB_ID)).thenReturn(false);

        Future<BuildResult> future = executor.submit(() -> {
            Thread.sleep(30_000);
            return someBuildResult();
        });

        // Without a pull to exclude, the build budget applies in full and the wait has to give up.
        assertThatThrownBy(() -> buildJobManagementService.awaitBuildResult(future, BUILD_JOB_ID, BUILD_TIMEOUT_SECONDS)).isInstanceOf(TimeoutException.class);
    }

    @Test
    void awaitBuildResult_returnsImmediatelyForAnAlreadyCompletedBuild() throws Exception {
        setUpService();

        BuildResult expectedResult = someBuildResult();
        Future<BuildResult> future = executor.submit(() -> expectedResult);

        assertThat(buildJobManagementService.awaitBuildResult(future, BUILD_JOB_ID, BUILD_TIMEOUT_SECONDS)).isSameAs(expectedResult);
    }

    private static BuildResult someBuildResult() {
        return new BuildResult("main", "abc123", "def456", List.of(), true);
    }
}
