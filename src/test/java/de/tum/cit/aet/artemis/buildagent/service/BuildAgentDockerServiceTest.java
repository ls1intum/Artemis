package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
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
import com.github.dockerjava.api.model.PullResponseItem;
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

    /**
     * Puts the docker client back to a pull that simply succeeds.
     * <p>
     * The mock is built once per class in the base class {@code @BeforeAll} and is never reset, so a test that stubs
     * the pull would otherwise keep deciding how the pull behaves for every test that runs after it. That coupling is
     * invisible until one of those later tests reaches the pull without stubbing it, and it only shows up in a
     * particular execution order. Re-establishing the default here makes every test start from the same state,
     * whatever ran before it.
     */
    @BeforeEach
    void resetPullBehaviour() throws InterruptedException {
        PullImageCmd defaultPullImageCmd = mock(PullImageCmd.class);
        doReturn(defaultPullImageCmd).when(dockerClient).pullImageCmd(anyString());
        doReturn(defaultPullImageCmd).when(defaultPullImageCmd).withPlatform(anyString());
        BuildAgentDockerService.MyPullImageResultCallback defaultCallback = mock(BuildAgentDockerService.MyPullImageResultCallback.class);
        doReturn(defaultCallback).when(defaultPullImageCmd).exec(any(BuildAgentDockerService.MyPullImageResultCallback.class));
        lenient().when(defaultCallback.awaitFinished(anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

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
        var build = new BuildJobQueueItem("1", "job1", buildAgent, 1, 1, 1, 1, 1, BuildStatus.SUCCESSFUL, null, null, buildConfig, null, null, null, null);
        // The Docker client mock is shared by the whole class, so drop what earlier tests recorded on it. This keeps its
        // stubs but makes the verification below speak about this test only.
        clearInvocations(dockerClient);
        // Pull image
        try {
            buildAgentDockerService.pullDockerImage(build, new BuildLogsMap());
        }
        catch (LocalCIException e) {
            // Expected exception
            if (!(e.getCause() instanceof NotFoundException)) {
                throw e;
            }
        }

        // Verify that a pull was attempted. The count is deliberately not pinned: the service retries a failed pull,
        // so how often it gets here depends on how the pull fails rather than on what this test is about.
        verify(dockerClient, atLeastOnce()).pullImageCmd("test-image-name");
    }

    @Test
    void testPullDockerImageFailsFastWhenPullMakesNoProgress() throws InterruptedException, IOException {
        var build = mockPendingImagePull();
        // A pull that is not getting through at all: it never finishes and the daemon reports nothing, so the
        // progress counter never moves.
        when(pullImageCallback.awaitFinished(anyLong(), any(TimeUnit.class))).thenReturn(false);
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
        when(pullImageCallback.awaitFinished(anyLong(), any(TimeUnit.class))).thenReturn(false);
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
    void testPullDockerImageClosesCallbackAndRestoresInterruptStatusWhenInterrupted() throws InterruptedException, IOException {
        var build = mockPendingImagePull();
        doThrow(new InterruptedException()).when(pullImageCallback).awaitFinished(anyLong(), any(TimeUnit.class));

        try {
            assertThatThrownBy(() -> buildAgentDockerService.pullDockerImage(build, buildLogsMap)).isInstanceOf(LocalCIException.class).rootCause()
                    .isInstanceOf(InterruptedException.class);

            verify(pullImageCallback).close();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(buildAgentDockerService.isImagePullInProgress(build.id())).isFalse();
        }
        finally {
            Thread.interrupted();
            buildLogsMap.removeBuildLogs(build.id());
        }
    }

    @Test
    void testProgressingPullThatOutlastsThePollIntervalIsNotAborted() throws Exception {
        // Regression test: a healthy pull that keeps reporting progress and takes longer than a single poll interval must
        // run to completion. Before the fix, awaitPullCompletion sliced the wait with docker-java's
        // ResultCallbackTemplate.awaitCompletion(timeout, unit), which closes (aborts) the pull stream in its finally
        // block on every call. The first timed-out slice therefore killed any pull longer than the poll interval, and the
        // build failed with "Could not pull Docker image ...". This test drives a real callback, so it exercises that
        // real docker-java behaviour rather than a mock.

        // Shorten the slice so the pull only has to outlast a few tens of milliseconds instead of the 5s default.
        long originalPollInterval = (long) ReflectionTestUtils.getField(buildAgentDockerService, "pullProgressPollIntervalMillis");
        ReflectionTestUtils.setField(buildAgentDockerService, "pullProgressPollIntervalMillis", 20L);

        AtomicBoolean pullCompleted = new AtomicBoolean(false);
        AtomicBoolean streamClosed = new AtomicBoolean(false);
        AtomicInteger inspectCount = new AtomicInteger();

        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        InspectImageResponse pulledImage = new InspectImageResponse().withArch("amd64");
        // The first two inspects miss, which is what drives the code into the pull. The inspect after the pull only finds
        // the image if the pull was actually allowed to finish - a prematurely aborted pull leaves nothing behind, exactly
        // as on a real daemon.
        doAnswer(invocation -> {
            if (inspectCount.incrementAndGet() <= 2) {
                throw new NotFoundException("");
            }
            if (pullCompleted.get()) {
                return pulledImage;
            }
            throw new NotFoundException("");
        }).when(inspectImageCmd).exec();

        var realCallback = new BuildAgentDockerService.MyPullImageResultCallback();
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        doReturn(pullImageCmd).when(dockerClient).pullImageCmd(anyString());
        doReturn(pullImageCmd).when(pullImageCmd).withPlatform(anyString());

        Thread pullFeeder = new Thread(() -> {
            try {
                // Report progress for longer than one poll interval, then finish successfully.
                for (int i = 0; i < 8 && !streamClosed.get(); i++) {
                    realCallback.onNext(progressItem("Downloading layer " + i));
                    Thread.sleep(15);
                }
                if (!streamClosed.get()) {
                    realCallback.onNext(progressItem("Status: Downloaded newer image for test-image-name"));
                    pullCompleted.set(true);
                    realCallback.onComplete();
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-pull-feeder");

        doAnswer(invocation -> {
            // Mirror the real daemon: the pull starts on exec and reports back through the callback. onStart wires the
            // stream docker-java closes on abort, so the feeder can tell when the pull was cut short.
            realCallback.onStart(() -> streamClosed.set(true));
            pullFeeder.start();
            return realCallback;
        }).when(pullImageCmd).exec(any(BuildAgentDockerService.MyPullImageResultCallback.class));

        BuildConfig buildConfig = new BuildConfig("echo 'test'", "test-image-name", "test", "test", "test", "test", null, null, false, false, null, 0, null, null, null, null);
        BuildAgentDTO buildAgent = new BuildAgentDTO("buildagent1", "address1", "buildagent1");
        var build = new BuildJobQueueItem("progressing-pull-job", "job1", buildAgent, 1, 1, 1, 1, 1, BuildStatus.SUCCESSFUL, null, null, buildConfig, null);

        try {
            buildAgentDockerService.pullDockerImage(build, buildLogsMap);

            assertThat(pullCompleted).as("the pull was allowed to run to completion instead of being aborted mid-pull").isTrue();
            assertThat(buildAgentDockerService.isImagePullInProgress(build.id())).isFalse();
        }
        finally {
            pullFeeder.interrupt();
            pullFeeder.join(1000);
            ReflectionTestUtils.setField(buildAgentDockerService, "pullProgressPollIntervalMillis", originalPollInterval);
            buildLogsMap.removeBuildLogs(build.id());
        }
    }

    /**
     * Builds a pull response item carrying the given status, the way the Docker daemon reports pull progress.
     *
     * @param status the status line, e.g. a layer download step or the final success message
     * @return a pull response item with that status
     */
    private static PullResponseItem progressItem(String status) {
        PullResponseItem item = new PullResponseItem();
        ReflectionTestUtils.setField(item, "status", status);
        return item;
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
        when(pullImageCallback.awaitFinished(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
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
