package de.tum.cit.aet.artemis.buildagent.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.model.Container;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

/**
 * Pure unit test (no Spring context, no real Docker) for the orphan-reaping logic of {@link HyperionGenSandboxReaperService}: only containers that are both older than the expiry
 * threshold AND carry the {@link InteractiveSandboxService#SANDBOX_CONTAINER_PREFIX} prefix are force-removed. The prefix and age guards are the load-bearing safety mechanism — a
 * regression in either would let the reaper force-remove a live CI build container or a still-running generation session.
 */
class HyperionGenSandboxReaperServiceTest {

    private static final int EXPIRY_MINUTES = 90;

    private BuildAgentConfiguration buildAgentConfiguration;

    private DockerClient dockerClient;

    private HyperionGenSandboxReaperService service;

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        dockerClient = mock(DockerClient.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        when(buildAgentConfiguration.getDockerClient()).thenReturn(dockerClient);
        when(buildAgentConfiguration.isDockerAvailable()).thenReturn(true);
        service = new HyperionGenSandboxReaperService(buildAgentConfiguration, taskScheduler);
        // The @PostConstruct scheduler hook only runs under Spring; the @Value-injected threshold must be set explicitly for this pure unit test.
        ReflectionTestUtils.setField(service, "generationContainerExpiryMinutes", EXPIRY_MINUTES);
    }

    /**
     * Stubs the container listing and the per-container removal command, returning the mocked remove command so the test can verify whether {@code exec()} was invoked.
     */
    private RemoveContainerCmd stubListAndRemove(Container container) {
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(listContainersCmd).when(listContainersCmd).withShowAll(true);
        doReturn(List.of(container)).when(listContainersCmd).exec();

        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        doReturn(removeContainerCmd).when(dockerClient).removeContainerCmd(anyString());
        doReturn(removeContainerCmd).when(removeContainerCmd).withForce(true);
        return removeContainerCmd;
    }

    private static Container container(String name, long createdEpochSeconds) {
        Container container = mock(Container.class);
        doReturn(new String[] { name }).when(container).getNames();
        doReturn(createdEpochSeconds).when(container).getCreated();
        doReturn("container-id").when(container).getId();
        return container;
    }

    @Test
    void shouldForceRemoveContainerWhenOlderThanThresholdAndPrefixMatches() {
        long createdWellBeyondExpiry = Instant.now().getEpochSecond() - (EXPIRY_MINUTES + 10) * 60L;
        Container container = container("/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX + "abc", createdWellBeyondExpiry);
        RemoveContainerCmd removeContainerCmd = stubListAndRemove(container);

        service.reapOrphanedSessions();

        verify(dockerClient, times(1)).removeContainerCmd("container-id");
        verify(removeContainerCmd, times(1)).withForce(true);
        verify(removeContainerCmd, times(1)).exec();
    }

    @Test
    void shouldNotRemoveContainerWhenYoungerThanThreshold() {
        long createdJustNow = Instant.now().getEpochSecond() - 60L;
        Container container = container("/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX + "abc", createdJustNow);
        stubListAndRemove(container);

        service.reapOrphanedSessions();

        verify(dockerClient, never()).removeContainerCmd(anyString());
    }

    @Test
    void shouldNeverRemoveContainerWhenPrefixDoesNotMatchEvenIfOld() {
        long createdWellBeyondExpiry = Instant.now().getEpochSecond() - (EXPIRY_MINUTES + 10) * 60L;
        // A CI build container that is old enough to expire but carries the foreign prefix — it must never be touched by the generation reaper.
        Container container = container("/local-ci-build", createdWellBeyondExpiry);
        stubListAndRemove(container);

        service.reapOrphanedSessions();

        verify(dockerClient, never()).removeContainerCmd(anyString());
    }
}
