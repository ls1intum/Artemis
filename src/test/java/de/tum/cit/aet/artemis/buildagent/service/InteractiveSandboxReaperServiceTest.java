package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.model.Container;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/**
 * Unit tests for the inactivity-based reaping contract: a long-lived but active session must survive, while a genuinely idle or orphaned container must be collected. Uses a real
 * {@link InteractiveSandboxService} so the reaper reads the same lock-free activity registry the exec path writes.
 */
class InteractiveSandboxReaperServiceTest {

    private static final long EXPIRY_MINUTES = 90;

    private static final long OLDER_THAN_EXPIRY_SECONDS = (EXPIRY_MINUTES + 60) * 60;

    private DockerClient dockerClient;

    private InteractiveSandboxService interactiveSandboxService;

    private InteractiveSandboxRelayHandler relayHandler;

    private InteractiveSandboxReaperService reaperService;

    @BeforeEach
    void setUp() {
        BuildAgentConfiguration buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        dockerClient = mock(DockerClient.class);
        doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        doReturn(dockerClient).when(buildAgentConfiguration).getDockerClient();

        interactiveSandboxService = new InteractiveSandboxService(buildAgentConfiguration);
        ReflectionTestUtils.setField(interactiveSandboxService, "buildAgentShortName", "agent");
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(InteractiveSandboxService.class)).thenReturn(interactiveSandboxService);
        // A real relay handler holding the session permits, wired without registering its topic listener (no Docker/topics needed): the reaper only calls back into releaseIfOwned.
        relayHandler = new InteractiveSandboxRelayHandler(applicationContext, mock(DistributedDataAccessService.class), mock(SharedQueueProcessingService.class),
                mock(BuildAgentInformationService.class));
        ReflectionTestUtils.setField(relayHandler, "buildAgentShortName", "agent");
        ReflectionTestUtils.setField(relayHandler, "maxGenerationSandboxSlots", 1);
        ReflectionTestUtils.setField(relayHandler, "sandboxSlotPermits", new Semaphore(1));
        reaperService = new InteractiveSandboxReaperService(buildAgentConfiguration, applicationContext, relayHandler, mock(TaskScheduler.class));
        ReflectionTestUtils.setField(reaperService, "sandboxContainerExpiryMinutes", (int) EXPIRY_MINUTES);
    }

    private Semaphore sandboxSlotPermits() {
        return (Semaphore) ReflectionTestUtils.getField(relayHandler, "sandboxSlotPermits");
    }

    @SuppressWarnings("unchecked")
    private Set<String> ownedSessionIds() {
        return (Set<String>) ReflectionTestUtils.getField(relayHandler, "ownedSessionIds");
    }

    private Container sandboxContainer(String id, String shortName, long createdEpochSecond) {
        Container container = mock(Container.class);
        doReturn(id).when(container).getId();
        doReturn(new String[] { "/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX + "agent-" + shortName }).when(container).getNames();
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

    @Test
    void shouldIgnoreAnotherAgentsSandboxContainersOnTheSameDockerHost() {
        long createdLongAgo = Instant.now().getEpochSecond() - OLDER_THAN_EXPIRY_SECONDS;
        Container ownOrphan = sandboxContainer("own-id", "agent-own", createdLongAgo);
        Container otherAgentOrphan = mock(Container.class);
        doReturn("other-agent-id").when(otherAgentOrphan).getId();
        doReturn(new String[] { "/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX + "other-agent-orphan" }).when(otherAgentOrphan).getNames();
        doReturn(createdLongAgo).when(otherAgentOrphan).getCreated();
        givenContainers(ownOrphan, otherAgentOrphan);

        reaperService.reapOrphanedSessions();

        verify(dockerClient).removeContainerCmd("own-id");
        verify(dockerClient, never()).removeContainerCmd("other-agent-id");
    }

    @Test
    void shouldReleaseTheRelayPermitWhenReapingAnOwnedOrphanedSession() {
        long createdLongAgo = Instant.now().getEpochSecond() - OLDER_THAN_EXPIRY_SECONDS;
        Container orphanContainer = sandboxContainer("orphan-id", "orphan", createdLongAgo);
        givenContainers(orphanContainer);
        // A relay session this agent still owns because its CREATE response (or DESTROY) was lost: the permit is held and the container is orphaned.
        ownedSessionIds().add("orphan-id");
        sandboxSlotPermits().acquireUninterruptibly();
        assertThat(sandboxSlotPermits().availablePermits()).isZero();

        reaperService.reapOrphanedSessions();

        verify(dockerClient).removeContainerCmd("orphan-id");
        // Exactly one permit is reclaimed and the session is no longer tracked, so repeated orphaning cannot starve the agent of generation capacity.
        assertThat(sandboxSlotPermits().availablePermits()).isOne();
        assertThat(ownedSessionIds()).doesNotContain("orphan-id");
    }

    @Test
    void shouldNotReleaseAPermitWhenReapingAContainerItDoesNotOwn() {
        long createdLongAgo = Instant.now().getEpochSecond() - OLDER_THAN_EXPIRY_SECONDS;
        Container orphanContainer = sandboxContainer("orphan-id", "orphan", createdLongAgo);
        givenContainers(orphanContainer);
        // A permit held by a DIFFERENT live session; the reaped container is not one this handler owns, so reconciliation must release nothing — and stay idempotent across sweeps.
        ownedSessionIds().add("other-live-session");
        sandboxSlotPermits().acquireUninterruptibly();
        assertThat(sandboxSlotPermits().availablePermits()).isZero();

        reaperService.reapOrphanedSessions();
        reaperService.reapOrphanedSessions();

        verify(dockerClient, times(2)).removeContainerCmd("orphan-id");
        assertThat(sandboxSlotPermits().availablePermits()).isZero();
        assertThat(ownedSessionIds()).containsOnly("other-live-session");
    }
}
