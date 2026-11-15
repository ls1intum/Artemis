package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

@Isolated("Changes os.name while exercising Docker platform selection.")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class BuildAgentDockerImagePolicyTest {

    private final BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);

    private final DockerClient docker = mock(DockerClient.class);

    private final InspectImageCmd inspect = mock(InspectImageCmd.class);

    private final InspectImageResponse image = mock(InspectImageResponse.class);

    private final PullImageCmd pull = mock(PullImageCmd.class, RETURNS_SELF);

    private final BuildLogsMap logs = mock(BuildLogsMap.class);

    private final BuildAgentDockerService service = new BuildAgentDockerService(configuration, mock(DistributedDataAccessService.class), mock(BuildJobContainerService.class),
            mock(TaskScheduler.class));

    private String originalOs;

    @BeforeEach
    void setUp() {
        originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Linux");
        when(configuration.getDockerClient()).thenReturn(docker);
        when(configuration.isDockerAvailable()).thenReturn(true);
        when(docker.inspectImageCmd("image")).thenReturn(inspect);
        when(inspect.exec()).thenReturn(image);
        when(docker.pullImageCmd("image")).thenReturn(pull);
        ReflectionTestUtils.setField(service, "imageArchitecture", "arm64");
        ReflectionTestUtils.setField(service, "imageCleanupEnabled", false);
        ReflectionTestUtils.setField(service, "imagePullTimeoutSeconds", 10);
        ReflectionTestUtils.setField(service, "imagePullStallTimeoutSeconds", 10);
    }

    @AfterEach
    void restoreOs() {
        if (originalOs == null) {
            System.clearProperty("os.name");
        }
        else {
            System.setProperty("os.name", originalOs);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "arm64" })
    void missingArchitectureMetadataAndMatchingArchitectureUseCachedImageWithoutPull(String architecture) {
        when(image.getArch()).thenReturn(architecture);
        assertThatCode(() -> service.pullDockerImage(job(), logs)).doesNotThrowAnyException();
        verify(docker, org.mockito.Mockito.never()).pullImageCmd(any());
        assertThat(service.isImagePullInProgress("job")).isFalse();
    }

    @Test
    void mismatchingImageFailsOnLinuxButRunsOnMacArm() {
        when(image.getArch()).thenReturn("amd64");
        assertThatThrownBy(() -> service.pullDockerImage(job(), logs)).isInstanceOf(LocalCIException.class).hasMessageContaining("not compatible");
        System.setProperty("os.name", "Mac OS X");
        assertThatCode(() -> service.pullDockerImage(job(), logs)).doesNotThrowAnyException();
        System.clearProperty("os.name");
        assertThatThrownBy(() -> service.pullDockerImage(job(), logs)).isInstanceOf(LocalCIException.class).hasMessageContaining("not compatible");
        assertThat(service.isImagePullInProgress("job")).isFalse();
    }

    @Test
    void secondInspectionCanFindImagePulledByAnotherJob() {
        when(image.getArch()).thenReturn("arm64");
        when(inspect.exec()).thenThrow(new NotFoundException("not cached")).thenReturn(image);
        service.pullDockerImage(job(), logs);
        verify(docker, org.mockito.Mockito.never()).pullImageCmd(any());
        verify(inspect, times(2)).exec();
    }

    @Test
    void macArmFallsBackToAmd64OnlyForMatchingManifestFailureAndChecksResult() throws Exception {
        System.setProperty("os.name", "Mac OS X");
        when(image.getArch()).thenReturn("amd64");
        when(inspect.exec()).thenThrow(new NotFoundException("missing")).thenThrow(new NotFoundException("still missing")).thenReturn(image);
        var callback = mock(BuildAgentDockerService.MyPullImageResultCallback.class);
        when(callback.awaitFinished(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(pull.exec(any(BuildAgentDockerService.MyPullImageResultCallback.class)))
                .thenThrow(new IllegalStateException(null, new IllegalArgumentException("no matching manifest for linux/arm64"))).thenReturn(callback);
        service.pullDockerImage(job(), logs);
        verify(pull).withPlatform("arm64");
        verify(pull).withPlatform("amd64");
        verify(pull, times(2)).exec(any(BuildAgentDockerService.MyPullImageResultCallback.class));
        assertThat(service.isImagePullInProgress("job")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ordinary", "null", "cycle" })
    void macArmDoesNotRetryUnrelatedOrCyclicFailures(String shape) {
        System.setProperty("os.name", "Mac OS X");
        when(inspect.exec()).thenThrow(new NotFoundException("missing"));
        RuntimeException failure = shape.equals("null") ? new IllegalStateException((String) null) : new IllegalStateException("network unavailable");
        if (shape.equals("cycle")) {
            failure.initCause(new IllegalStateException("nested cause", failure));
        }
        when(pull.exec(any(BuildAgentDockerService.MyPullImageResultCallback.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.pullDockerImage(job(), logs)).isInstanceOf(LocalCIException.class).hasMessage("Error while pulling docker image image").hasCause(failure);
        verify(pull, times(1)).exec(any(BuildAgentDockerService.MyPullImageResultCallback.class));
        assertThat(service.isImagePullInProgress("job")).isFalse();
    }

    private BuildJobQueueItem job() {
        var config = new BuildConfig("script", "image", "commit", "assignment", "tests", "main", null, null, false, false, List.of(), 60, null, null, null, null);
        return new BuildJobQueueItem("job", "job", new BuildAgentDTO("agent", "address", "agent"), 1, 2, 3, 0, 1, null, null, null, config, null);
    }
}
