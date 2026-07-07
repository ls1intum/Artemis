package de.tum.cit.aet.artemis.buildagent.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
 * Unit tests for the inactivity-based reaping contract: a long-lived but active session must survive, while a genuinely idle or orphaned container must be collected. Uses a real
 * {@link InteractiveSandboxService} so the reaper reads the same lock-free activity registry the exec path writes.
 */
class InteractiveSandboxReaperServiceTest {

    private static final long EXPIRY_MINUTES = 90;

    private static final long OLDER_THAN_EXPIRY_SECONDS = (EXPIRY_MINUTES + 60) * 60;

    private DockerClient dockerClient;

    private InteractiveSandboxService interactiveSandboxService;

    private InteractiveSandboxReaperService reaperService;

    @BeforeEach
    void setUp() {
        BuildAgentConfiguration buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        dockerClient = mock(DockerClient.class);
        doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        doReturn(dockerClient).when(buildAgentConfiguration).getDockerClient();

        interactiveSandboxService = new InteractiveSandboxService(buildAgentConfiguration);
        reaperService = new InteractiveSandboxReaperService(buildAgentConfiguration, interactiveSandboxService, mock(TaskScheduler.class));
        ReflectionTestUtils.setField(reaperService, "sandboxContainerExpiryMinutes", (int) EXPIRY_MINUTES);
    }

    private Container sandboxContainer(String id, String shortName, long createdEpochSecond) {
        Container container = mock(Container.class);
        doReturn(id).when(container).getId();
        doReturn(new String[] { "/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX + shortName }).when(container).getNames();
        doReturn(createdEpochSecond).when(container).getCreated();
        return container;
    }

    private void givenContainers(Container... containers) {
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        doReturn(listContainersCmd).when(dockerClient).listContainersCmd();
        doReturn(listContainersCmd).when(listContainersCmd).withShowAll(true);
        doReturn(List.of(containers)).when(listContainersCmd).exec();

        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        doReturn(removeContainerCmd).when(dockerClient).removeContainerCmd(anyString());
        doReturn(removeContainerCmd).when(removeContainerCmd).withForce(true);
    }

    @Test
    void shouldNotReapRecentlyActiveButOldContainer() {
        long createdLongAgo = Instant.now().getEpochSecond() - OLDER_THAN_EXPIRY_SECONDS;
        Container activeContainer = sandboxContainer("active-id", "active", createdLongAgo);
        givenContainers(activeContainer);
        // A healthy long-running session: created well past the expiry window, but an operation just refreshed its activity stamp.
        interactiveSandboxService.markActive("active-id");

        reaperService.reapOrphanedSessions();

        verify(dockerClient, never()).removeContainerCmd(anyString());
    }

    @Test
    void shouldReapIdleOrphanedContainer() {
        long createdLongAgo = Instant.now().getEpochSecond() - OLDER_THAN_EXPIRY_SECONDS;
        // No activity ever recorded for this container (e.g. left behind by a previous agent process): the reaper falls back to creation time and collects it.
        Container orphanContainer = sandboxContainer("orphan-id", "orphan", createdLongAgo);
        givenContainers(orphanContainer);

        reaperService.reapOrphanedSessions();

        verify(dockerClient).removeContainerCmd("orphan-id");
    }

    @Test
    void shouldReapOnlyTheIdleContainerWhenBothPresent() {
        long createdLongAgo = Instant.now().getEpochSecond() - OLDER_THAN_EXPIRY_SECONDS;
        Container activeContainer = sandboxContainer("active-id", "active", createdLongAgo);
        Container orphanContainer = sandboxContainer("orphan-id", "orphan", createdLongAgo);
        givenContainers(activeContainer, orphanContainer);
        interactiveSandboxService.markActive("active-id");

        reaperService.reapOrphanedSessions();

        verify(dockerClient).removeContainerCmd("orphan-id");
        verify(dockerClient, never()).removeContainerCmd("active-id");
    }

    @Test
    void shouldNotReapYoungOrphanedContainerViaCreationTimeFallback() {
        // No activity recorded, but created just now: the creation-time fallback keeps it until it is genuinely idle past the threshold.
        Container youngOrphan = sandboxContainer("young-id", "young", Instant.now().getEpochSecond());
        givenContainers(youngOrphan);

        reaperService.reapOrphanedSessions();

        verify(dockerClient, never()).removeContainerCmd(anyString());
    }

    @Test
    void shouldIgnoreNonSandboxContainersRegardlessOfAge() {
        Container ciContainer = mock(Container.class);
        doReturn("ci-id").when(ciContainer).getId();
        doReturn(new String[] { "/local-ci-somebuild" }).when(ciContainer).getNames();
        doReturn(Instant.now().getEpochSecond() - OLDER_THAN_EXPIRY_SECONDS).when(ciContainer).getCreated();
        givenContainers(ciContainer);

        reaperService.reapOrphanedSessions();

        verify(dockerClient, never()).removeContainerCmd(anyString());
    }
}
