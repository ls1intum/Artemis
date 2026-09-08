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
 * Removes inactive sandbox containers belonging to this agent. Active operations prevent reaping; previous-process containers use creation time as their last activity.
 * Runs even at zero hosting capacity so disabling generation does not strand old containers. Student build containers use a separate cleanup path.
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class InteractiveSandboxReaperService {

    private static final Logger log = LoggerFactory.getLogger(InteractiveSandboxReaperService.class);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final ApplicationContext applicationContext;

    /** Owns the per-agent session permits, and only exists when this agent opted into hosting — hence optional, since the sweep must not be gated on that opt-in. */
    private final Optional<InteractiveSandboxRelayHandler> relayHandler;

    private final TaskScheduler taskScheduler;

    /** Idle threshold between operations, not a maximum session age. */
    @Value("${artemis.continuous-integration.build-agent.generation-sandbox-idle-timeout-minutes:90}")
    private int sandboxContainerExpiryMinutes;

    @Value("${artemis.continuous-integration.build-agent.generation-sandbox-cleanup-interval-minutes:15}")
    private int sandboxCleanupScheduleMinutes;

    public InteractiveSandboxReaperService(BuildAgentConfiguration buildAgentConfiguration, ApplicationContext applicationContext,
            Optional<InteractiveSandboxRelayHandler> relayHandler, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.applicationContext = applicationContext;
        this.relayHandler = relayHandler;
        this.taskScheduler = taskScheduler;
    }

    /** Validates the configured thresholds and schedules the periodic sweep. */
    @PostConstruct
    public void scheduleCleanup() {
        if (sandboxContainerExpiryMinutes <= 0 || sandboxCleanupScheduleMinutes <= 0) {
            throw new IllegalArgumentException("Generation sandbox cleanup interval and idle timeout must be positive");
        }
        taskScheduler.scheduleAtFixedRate(this::reapOrphanedSessions, Instant.now().plusSeconds(30), Duration.ofMinutes(sandboxCleanupScheduleMinutes));
        if (relayHandler.isEmpty()) {
            // At zero capacity this process owns no sessions. Defer cleanup until after context refresh to avoid eagerly constructing the sandbox service.
            taskScheduler.schedule(this::removeLeftoverSessionsFromAPreviousProcess, Instant.now());
        }
    }

    private void removeLeftoverSessionsFromAPreviousProcess() {
        try {
            int removed = interactiveSandboxService().removeSessionsForCurrentAgent();
            if (removed > 0) {
                log.info("Removed {} leftover interactive sandbox container(s) on a build agent that does not host generation sandboxes.", removed);
            }
        }
        catch (RuntimeException e) {
            log.warn("Could not remove leftover interactive sandbox containers at startup; the periodic idle sweep will retry: {}", e.getMessage());
        }
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

        Set<String> ownedSessionsBeforeListing = relayHandler.map(InteractiveSandboxRelayHandler::ownedSessionIdsSnapshot).orElseGet(Set::of);
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
                    relayHandler.ifPresent(handler -> handler.releaseIfOwned(container.getId()));
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
                    relayHandler.ifPresent(handler -> handler.releaseIfOwned(sessionId));
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

    private long lastActivityEpochSecond(Container container) {
        Optional<Instant> lastActivity = interactiveSandboxService().lastActivity(container.getId());
        return lastActivity.map(Instant::getEpochSecond).orElseGet(container::getCreated);
    }
}
