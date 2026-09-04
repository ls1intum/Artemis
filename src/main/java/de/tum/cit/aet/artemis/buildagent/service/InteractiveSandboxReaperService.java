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
 * which age-based reaping would kill mid-run and break the next {@code exec}. {@link InteractiveSandboxService} refreshes a per-container last-activity stamp on every operation,
 * and a container is removed only once it has been idle past the threshold. A container with no known activity — one left behind by a previous agent process — falls back to its
 * creation time, so genuine orphans are still collected on the first sweep after an agent restart.
 * <p>
 * It runs on every build agent, not only on those that opted into hosting generation sandboxes: an agent SIGKILLed mid-session and then redeployed with
 * {@code max-generation-sandbox-slots=0} still has the containers, and no other bean would ever collect them.
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

    /** Owns the per-agent session permits, and only exists when this agent opted into hosting — hence optional, since the sweep must not be gated on that opt-in. */
    private final Optional<InteractiveSandboxRelayHandler> relayHandler;

    private final TaskScheduler taskScheduler;

    /**
     * A sandbox container idle for longer than this is considered orphaned. It need only exceed the longest single operation an agent can drive (a multi-minute build or verify),
     * not the whole session wall-clock, because any activity refreshes the stamp.
     */
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
            // No relay handler will reconcile leftovers before advertising capacity here, so sweep once at startup. A pure name-prefix sweep is safe at zero capacity: this
            // process owns no sessions, so every container carrying the prefix is left over from a previous one. Scheduled rather than called inline because it resolves the
            // lazy InteractiveSandboxService, which must not be built during context refresh on an agent that hosts nothing. The periodic idle sweep is the backstop.
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
