package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class BuildAgentDockerImageAvailabilityTest {

    private static final String IMAGE = "registry.example/artemis-java:stable";

    private static final String IMAGE_ID = "sha256:" + "a".repeat(64);

    private BuildAgentConfiguration buildAgentConfiguration;

    private DockerClient dockerClient;

    private BuildAgentDockerService service;

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        dockerClient = mock(DockerClient.class);
        when(buildAgentConfiguration.isDockerAvailable()).thenReturn(true);
        when(buildAgentConfiguration.getDockerClient()).thenReturn(dockerClient);
        service = new BuildAgentDockerService(buildAgentConfiguration, mock(DistributedDataAccessService.class), mock(BuildJobContainerService.class), mock(TaskScheduler.class));
        ReflectionTestUtils.setField(service, "imageArchitecture", "amd64");
        ReflectionTestUtils.setField(service, "imageCleanupEnabled", false);
    }

    @Test
    void availableImageReturnsImmutableIdWithoutPulling() {
        InspectImageCmd inspectImageCmd = inspectCommand(new InspectImageResponse().withId(IMAGE_ID).withArch("amd64"));
        when(dockerClient.inspectImageCmd(IMAGE)).thenReturn(inspectImageCmd);

        assertThat(service.ensureDockerImageAvailable(IMAGE)).isEqualTo(IMAGE_ID);

        verify(dockerClient, never()).pullImageCmd(any());
    }

    @Test
    void concurrentRequestsOnFreshAgentPullOnlyOnce() throws Exception {
        AtomicBoolean imagePresent = new AtomicBoolean();
        CountDownLatch initialInspections = new CountDownLatch(2);
        when(dockerClient.inspectImageCmd(IMAGE)).thenAnswer(ignored -> {
            InspectImageCmd command = mock(InspectImageCmd.class);
            when(command.exec()).thenAnswer(invocation -> {
                if (!imagePresent.get()) {
                    initialInspections.countDown();
                    assertThat(initialInspections.await(5, TimeUnit.SECONDS)).isTrue();
                    throw new NotFoundException("missing");
                }
                return new InspectImageResponse().withId(IMAGE_ID).withArch("amd64");
            });
            return command;
        });
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(dockerClient.pullImageCmd(IMAGE)).thenReturn(pullImageCmd);
        when(pullImageCmd.withPlatform("amd64")).thenReturn(pullImageCmd);
        when(pullImageCmd.exec(any())).thenAnswer(invocation -> {
            imagePresent.set(true);
            PullImageResultCallback callback = invocation.getArgument(0);
            callback.onComplete();
            return callback;
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.ensureDockerImageAvailable(IMAGE));
            var second = executor.submit(() -> service.ensureDockerImageAvailable(IMAGE));

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(IMAGE_ID);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(IMAGE_ID);
        }
        verify(dockerClient, times(1)).pullImageCmd(IMAGE);
    }

    @Test
    void imageWithoutImmutableDockerIdIsRejected() {
        InspectImageCmd inspectImageCmd = inspectCommand(new InspectImageResponse().withId("not-an-image-id").withArch("amd64"));
        when(dockerClient.inspectImageCmd(IMAGE)).thenReturn(inspectImageCmd);

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.ensureDockerImageAvailable(IMAGE)).withMessageContaining("immutable image ID");
    }

    @Test
    void blankImageIsRejectedBeforeUsingDocker() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.ensureDockerImageAvailable(" ")).withMessageContaining("must not be blank");

        verify(buildAgentConfiguration, never()).getDockerClient();
    }

    private static InspectImageCmd inspectCommand(InspectImageResponse response) {
        InspectImageCmd command = mock(InspectImageCmd.class);
        when(command.exec()).thenReturn(response);
        return command;
    }
}
