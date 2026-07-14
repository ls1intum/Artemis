package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;

/**
 * Reaps orphaned interactive sandbox containers, i.e. those named with the {@link InteractiveSandboxService#SANDBOX_CONTAINER_PREFIX} prefix.
 * <p>
 * This is a dedicated counterpart to the CI build-container cleanup in {@link BuildAgentDockerService#cleanUpContainers()}: a sandbox session legitimately runs for a long time
 * (far longer than a CI build), so it needs its own, longer threshold, and the CI reaper's prefix never matches a sandbox container.
 * <p>
 * Reaping is by <em>inactivity</em>, not age: an agentic session can accumulate more than an hour of healthy wall-clock (many turns, each up to a multi-minute build or verify),
 * which age-based reaping would kill mid-run and break the next {@code exec}. {@link InteractiveSandboxService} refreshes a per-container last-activity stamp on every operation
 * (create, exec, copy), and a container is removed only once it has been idle past the threshold. A container with no known activity — one left behind by a previous agent
 * process — falls back to its creation time, so genuine orphans are still collected on the first sweep after an agent restart.
 *
 * @see BuildAgentDockerService#cleanUpContainers()
 * @see InteractiveSandboxService
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxReaperService {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxReaperService.class);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final ApplicationContext applicationContext;

    /**
     * The relay handler that owns the per-agent session permits. It is unconditionally present on a build-agent node (same {@link Profile}), so a plain dependency is correct; the
     * reaper calls back into it to release the permit of any orphaned relay session it reaps, so lost CREATE responses / failovers / lost DESTROYs cannot slowly deplete the
     * agent's
     * generation sandbox slot capacity between restarts.
     */
    private final InteractiveSandboxRelayHandler relayHandler;

    private final TaskScheduler taskScheduler;

    /**
     * A sandbox container idle (no operation) for longer than this is considered orphaned. The threshold need only exceed the longest single operation an agent can drive (a
     * multi-minute build or verify), not the whole session wall-clock, because any activity refreshes the stamp — so a healthy but hours-long session is never reaped.
     */
    @Value("${artemis.continuous-integration.build-agent.generation-sandbox-idle-timeout-minutes:90}")
    private int sandboxContainerExpiryMinutes;

    @Value("${artemis.continuous-integration.build-agent.generation-sandbox-cleanup-interval-minutes:15}")
    private int sandboxCleanupScheduleMinutes;

    public InteractiveSandboxReaperService(BuildAgentConfiguration buildAgentConfiguration, ApplicationContext applicationContext, InteractiveSandboxRelayHandler relayHandler,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.applicationContext = applicationContext;
        this.relayHandler = relayHandler;
        this.taskScheduler = taskScheduler;
    }

    // EventListener cannot be used here, as the bean is lazy
    // https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation
    @PostConstruct
    public void scheduleCleanup() {
        taskScheduler.scheduleAtFixedRate(this::reapOrphanedSessions, Instant.now().plusSeconds(30), Duration.ofMinutes(sandboxCleanupScheduleMinutes));
    }

    /**
     * Removes sandbox containers idle longer than the configured threshold; recently-active ones belong to a session still in progress and are left untouched.
     */
    public void reapOrphanedSessions() {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            log.debug("Docker is not available. Skipping interactive sandbox cleanup.");
            return;
        }

        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        long now = Instant.now().getEpochSecond();
        long idleThreshold = sandboxContainerExpiryMinutes * 60L;

        Set<String> ownedSessionsBeforeListing = relayHandler.ownedSessionIdsSnapshot();
        List<Container> currentAgentContainers;
        try {
            String namePrefix = interactiveSandboxService().containerNamePrefix();
            currentAgentContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(container -> InteractiveSandboxService.hasSandboxContainerName(container, namePrefix)).toList();
        }
        catch (Exception ex) {
            if (DockerUtil.isDockerNotAvailable(ex)) {
                log.debug("Docker is not available. Skipping interactive sandbox cleanup: {}", ex.getMessage());
                return;
            }
            log.error("Error while listing containers for interactive sandbox cleanup: {}", ex.getMessage(), ex);
            return;
        }

        reconcileMissingOwnedSessions(ownedSessionsBeforeListing, currentAgentContainers);
        List<Container> orphanedSandboxContainers = currentAgentContainers.stream().filter(container -> (now - lastActivityEpochSecond(container)) > idleThreshold).toList();
        if (orphanedSandboxContainers.isEmpty()) {
            return;
        }
        log.info("Found {} orphaned interactive sandbox containers", orphanedSandboxContainers.size());
        for (Container container : orphanedSandboxContainers) {
            try {
                if (interactiveSandboxService().reapSessionIfInactive(container.getId(), container.getCreated(), idleThreshold)) {
                    relayHandler.releaseIfOwned(container.getId());
                }
            }
            catch (Exception ex) {
                log.warn("Failed to reap orphaned interactive sandbox container {}: {}", container.getId(), ex.getMessage());
            }
        }
    }

    private void reconcileMissingOwnedSessions(Set<String> ownedSessionsBeforeListing, List<Container> currentAgentContainers) {
        Set<String> listedContainerIds = currentAgentContainers.stream().map(Container::getId).collect(Collectors.toSet());
        ownedSessionsBeforeListing.stream().filter(sessionId -> !listedContainerIds.contains(sessionId)).forEach(sessionId -> {
            try {
                if (!interactiveSandboxService().sessionExists(sessionId)) {
                    interactiveSandboxService().forgetActivity(sessionId);
                    relayHandler.releaseIfOwned(sessionId);
                    log.warn("Released the generation sandbox slot for externally removed session {}", sessionId);
                }
            }
            catch (RuntimeException ex) {
                log.warn("Could not reconcile missing interactive sandbox session {}: {}", sessionId, ex.getMessage());
            }
        });
    }

    private InteractiveSandboxService interactiveSandboxService() {
        return applicationContext.getBean(InteractiveSandboxService.class);
    }

    /**
     * The epoch-second of a container's last recorded activity, falling back to its creation time when this process has no activity record for it (e.g. a container left behind
     * by a previous agent process). Reading the in-process registry keeps the sweep cheap.
     */
    private long lastActivityEpochSecond(Container container) {
        Optional<Instant> lastActivity = interactiveSandboxService().lastActivity(container.getId());
        return lastActivity.map(Instant::getEpochSecond).orElseGet(container::getCreated);
    }
}
