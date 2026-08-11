package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Info;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BuildAgentDockerServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "badoservtst";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Autowired
    private BuildAgentDockerService buildAgentDockerService;

    @Autowired
    @Qualifier("hazelcastInstance")
    private HazelcastInstance hazelcastInstance;

    // The Spring-managed bean has to be used here: a manually constructed BuildLogsMap has maxLogLinesPerBuildJob = 0 and therefore silently drops every entry.
    @Autowired
    private BuildLogsMap buildLogsMap;

    @Test
    @Order(2)
    void testDeleteOldDockerImages() {
        // Save build job with outdated image to database
        ZonedDateTime buildStartDate = ZonedDateTime.now().minusDays(3);

        BuildJob buildJob = new BuildJob();
        buildJob.setDockerImage("test-image-name");
        buildJob.setBuildStartDate(buildStartDate);

        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

        dockerImageCleanupInfo.put("test-image-name", buildStartDate);

        buildJobRepository.save(buildJob);

        buildAgentDockerService.deleteOldDockerImages();

        // Verify that removeImageCmd() was called.
        verify(dockerClient, times(1)).removeImageCmd(anyString());
    }

    @Test
    @Order(1)
    void testDeleteOldDockerImages_NoOutdatedImages() {
        // Save build job to database
        ZonedDateTime buildStartDate = ZonedDateTime.now();

        BuildJob buildJob = new BuildJob();
        buildJob.setDockerImage("test-image-name");
        buildJob.setBuildStartDate(buildStartDate);

        buildJobRepository.save(buildJob);

        buildAgentDockerService.deleteOldDockerImages();

        // Verify that removeImageCmd() was not called.
        verify(dockerClient, never()).removeImageCmd(anyString());
    }

    @Test
    void testPullDockerImage() {
        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doThrow(new NotFoundException("")).when(inspectImageCmd).exec();
        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test-image-name", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
        BuildAgentDTO buildAgent = new BuildAgentDTO("buildagent1", "address1", "buildagent1");
        var build = new BuildJobQueueItem("1", "job1", buildAgent, 1, 1, 1, 1, 1, BuildStatus.SUCCESSFUL, null, null, buildConfig, null);
        // This test only cares that a missing image drives the code into a pull. Bound the wait so it cannot depend on
        // whatever the shared dockerClient mock still carries from another test: without a completing pull the service
        // would now correctly wait out the stall budget before giving up.
        int originalStallTimeout = (int) ReflectionTestUtils.getField(buildAgentDockerService, "imagePullStallTimeoutSeconds");
        ReflectionTestUtils.setField(buildAgentDockerService, "imagePullStallTimeoutSeconds", 1);
        // Pull image
        try {
            buildAgentDockerService.pullDockerImage(build, new BuildLogsMap());
        }
        catch (LocalCIException e) {
            // Expected: the image is missing, so either the pull itself reports the failure or it never gets through.
        }
        finally {
            ReflectionTestUtils.setField(buildAgentDockerService, "imagePullStallTimeoutSeconds", originalStallTimeout);
        }

        // Verify that a pull was attempted. The count is deliberately not pinned: the service retries a failed pull,
        // so how often it gets here depends on how the pull fails rather than on what this test is about.
        verify(dockerClient, atLeastOnce()).pullImageCmd("test-image-name");
    }

    @Test
    void testPullDockerImageFailsFastWhenPullMakesNoProgress() throws InterruptedException, IOException {
        var build = mockPendingImagePull();
        // A pull that is not getting through at all: the await never completes and the daemon reports nothing, so the
        // progress counter never moves.
        when(pullImageCallback.awaitCompletion(anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(pullImageCallback.progressCount()).thenReturn(0L);
        int originalStallTimeout = (int) ReflectionTestUtils.getField(buildAgentDockerService, "imagePullStallTimeoutSeconds");
        ReflectionTestUtils.setField(buildAgentDockerService, "imagePullStallTimeoutSeconds", 1);

        try {
            // This must not wait for the full pull budget: an unreachable registry is recognisable from the silence alone.
            assertThatThrownBy(() -> buildAgentDockerService.pullDockerImage(build, buildLogsMap)).isInstanceOf(LocalCIException.class).rootCause()
                    .hasMessageContaining("reported no progress");

            assertThat(buildLogsMap.getAndTruncateBuildLogs(build.id())).anyMatch(logEntry -> logEntry.log().contains("reported no progress"));
            // The job must not stay registered as pulling, otherwise stale detection would never look at it again.
            assertThat(buildAgentDockerService.isImagePullInProgress(build.id())).isFalse();
            // The pull is abandoned rather than left running in the background.
            verify(pullImageCallback).close();
        }
        finally {
            ReflectionTestUtils.setField(buildAgentDockerService, "imagePullStallTimeoutSeconds", originalStallTimeout);
            buildLogsMap.removeBuildLogs(build.id());
        }
    }

    @Test
    void testPullDockerImageFailsWhenAProgressingPullExceedsTheTotalTimeout() throws InterruptedException {
        var build = mockPendingImagePull();
        // A pull that keeps reporting progress but never arrives, so only the overall budget can stop it.
        when(pullImageCallback.awaitCompletion(anyLong(), any(TimeUnit.class))).thenReturn(false);
        AtomicLong reportedProgress = new AtomicLong();
        when(pullImageCallback.progressCount()).thenAnswer(invocation -> reportedProgress.incrementAndGet());
        int originalTimeout = (int) ReflectionTestUtils.getField(buildAgentDockerService, "imagePullTimeoutSeconds");
        ReflectionTestUtils.setField(buildAgentDockerService, "imagePullTimeoutSeconds", 1);

        try {
            assertThatThrownBy(() -> buildAgentDockerService.pullDockerImage(build, buildLogsMap)).isInstanceOf(LocalCIException.class).rootCause()
                    .hasMessageContaining("did not finish within");

            assertThat(buildLogsMap.getAndTruncateBuildLogs(build.id())).anyMatch(logEntry -> logEntry.log().contains("did not finish within"));
            assertThat(buildAgentDockerService.isImagePullInProgress(build.id())).isFalse();
        }
        finally {
            ReflectionTestUtils.setField(buildAgentDockerService, "imagePullTimeoutSeconds", originalTimeout);
            buildLogsMap.removeBuildLogs(build.id());
        }
    }

    @Test
    void testNonPositivePullStallTimeoutIsRejectedAtStartup() {
        int originalStallTimeout = (int) ReflectionTestUtils.getField(buildAgentDockerService, "imagePullStallTimeoutSeconds");
        try {
            ReflectionTestUtils.setField(buildAgentDockerService, "imagePullStallTimeoutSeconds", 0);
            assertThatThrownBy(() -> buildAgentDockerService.applicationReady()).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("image-pull-stall-timeout-seconds");
        }
        finally {
            ReflectionTestUtils.setField(buildAgentDockerService, "imagePullStallTimeoutSeconds", originalStallTimeout);
        }
    }

    @Test
    void testImagePullIsVisibleWhileItIsRunning() throws InterruptedException {
        var build = mockPendingImagePull();
        AtomicBoolean pullInProgressDuringPull = new AtomicBoolean(false);
        when(pullImageCallback.awaitCompletion(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            pullInProgressDuringPull.set(buildAgentDockerService.isImagePullInProgress(build.id()));
            return true;
        });

        try {
            buildAgentDockerService.pullDockerImage(build, buildLogsMap);

            // While the pull runs the job has no container yet, so stale detection has to be able to see that a pull is in flight.
            assertThat(pullInProgressDuringPull).isTrue();
            assertThat(buildAgentDockerService.isImagePullInProgress(build.id())).isFalse();
        }
        finally {
            buildLogsMap.removeBuildLogs(build.id());
        }
    }

    @Test
    void testNonPositivePullTimeoutIsRejectedAtStartup() {
        int originalTimeout = (int) ReflectionTestUtils.getField(buildAgentDockerService, "imagePullTimeoutSeconds");
        try {
            ReflectionTestUtils.setField(buildAgentDockerService, "imagePullTimeoutSeconds", 0);
            // A non-positive timeout would make every pull report as timed out, so the build agent must refuse to start instead.
            assertThatThrownBy(() -> buildAgentDockerService.applicationReady()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("image-pull-timeout-seconds");
        }
        finally {
            ReflectionTestUtils.setField(buildAgentDockerService, "imagePullTimeoutSeconds", originalTimeout);
        }
    }

    /**
     * Sets up a build job whose image is not present locally, so that {@link BuildAgentDockerService#pullDockerImage} reaches the actual pull.
     *
     * @return the build job to pull the image for
     */
    private BuildJobQueueItem mockPendingImagePull() {
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        // The image is missing on the first two inspects, which is what drives the code into the pull. The third inspect verifies the freshly pulled image.
        InspectImageResponse pulledImage = new InspectImageResponse().withArch("amd64");
        doThrow(new NotFoundException("")).doThrow(new NotFoundException("")).doReturn(pulledImage).when(inspectImageCmd).exec();

        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        doReturn(pullImageCmd).when(dockerClient).pullImageCmd(anyString());
        doReturn(pullImageCmd).when(pullImageCmd).withPlatform(anyString());
        pullImageCallback = mock(BuildAgentDockerService.MyPullImageResultCallback.class);
        doReturn(pullImageCallback).when(pullImageCmd).exec(any(BuildAgentDockerService.MyPullImageResultCallback.class));

        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test-image-name", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
        BuildAgentDTO buildAgent = new BuildAgentDTO("buildagent1", "address1", "buildagent1");
        return new BuildJobQueueItem("pull-timeout-job", "job1", buildAgent, 1, 1, 1, 1, 1, BuildStatus.SUCCESSFUL, null, null, buildConfig, null);
    }

    private BuildAgentDockerService.MyPullImageResultCallback pullImageCallback;

    @Test
    @Order(3)
    void testCheckUsableDiskSpaceThenCleanUp() {
        // Mock dockerClient.infoCmd().exec()
        InfoCmd infoCmd = mock(InfoCmd.class);
        Info info = mock(Info.class);
        doReturn(infoCmd).when(dockerClient).infoCmd();
        doReturn(info).when(infoCmd).exec();
        doReturn("/").when(info).getDockerRootDir();

        ZonedDateTime buildStartDate = ZonedDateTime.now();

        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");

        dockerImageCleanupInfo.put("test-image-name", buildStartDate);

        buildAgentDockerService.checkUsableDiskSpaceThenCleanUp();

        // Verify that removeImageCmd() was called.
        verify(dockerClient, times(2)).removeImageCmd("test-image-name");
    }

    @Test
    void testRemoveStrandedContainers() {

        // Mocks
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(listContainersCmd).when(listContainersCmd).withShowAll(true);

        Container mockContainer = mock(Container.class);
        doReturn(List.of(mockContainer)).when(listContainersCmd).exec();
        doReturn(new String[] { "/local-ci-dummycontainer" }).when(mockContainer).getNames();
        // Mock container creation time to be older than 5 minutes
        doReturn(Instant.now().getEpochSecond() - (6 * 60)).when(mockContainer).getCreated();
        doReturn("dummy-container-id").when(mockContainer).getId();

        buildAgentDockerService.cleanUpContainers();

        // Verify that stopContainerCmd() was called
        verify(dockerClient, times(1)).stopContainerCmd(anyString());

        // Mock container creation time to be younger than 5 minutes
        doReturn(Instant.now().getEpochSecond()).when(mockContainer).getCreated();

        buildAgentDockerService.cleanUpContainers();

        // Verify that stopContainerCmd() was not called a second time
        verify(dockerClient, times(1)).stopContainerCmd(anyString());

        // Mock container creation time to be older than 5 minutes
        doReturn(Instant.now().getEpochSecond() - (6 * 60)).when(mockContainer).getCreated();

        // Mock exception when stopping container
        StopContainerCmd stopContainerCmd = mock(StopContainerCmd.class);
        doReturn(stopContainerCmd).when(dockerClient).stopContainerCmd(anyString());
        doReturn(stopContainerCmd).when(stopContainerCmd).withTimeout(anyInt());
        doThrow(new RuntimeException("Container stopping failed")).when(stopContainerCmd).exec();

        buildAgentDockerService.cleanUpContainers();

        // Verify that killContainerCmd() was called
        verify(dockerClient, times(1)).killContainerCmd(anyString());

        // Mock NotModified exception when stopping container
        doThrow(new NotModifiedException("Container not running")).when(stopContainerCmd).exec();
        buildAgentDockerService.cleanUpContainers();

        // Verify that removeContainerCmd() was called
        verify(dockerClient, times(1)).removeContainerCmd(anyString());
    }
}
