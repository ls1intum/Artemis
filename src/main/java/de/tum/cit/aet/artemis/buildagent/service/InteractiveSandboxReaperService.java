package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
 * This is a dedicated counterpart to the CI build-container cleanup in {@link BuildAgentDockerService#cleanUpContainers()}: a sandbox session legitimately runs for several minutes
 * (far longer than a CI build), so it needs its own, longer expiry threshold, and the CI reaper's prefix never matches a sandbox container. A container is only removed once it is
 * older than the configured threshold, so a session in progress is never reaped. On agent restart the first sweep removes any sandbox container left behind by a previous process.
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

    private final TaskScheduler taskScheduler;

    /**
     * Sandbox containers older than this (by creation time) are considered orphaned. The threshold must exceed the maximum wall-clock of a live session (the agent turns plus the
     * two verification builds) so an in-progress session is never reaped; the default is set comfortably above that worst case.
     */
    @Value("${artemis.continuous-integration.build-agent.generation-container-expiry-minutes:90}")
    private int sandboxContainerExpiryMinutes;

    @Value("${artemis.continuous-integration.build-agent.generation-cleanup-schedule-minutes:15}")
    private int sandboxCleanupScheduleMinutes;

    public InteractiveSandboxReaperService(BuildAgentConfiguration buildAgentConfiguration, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.taskScheduler = taskScheduler;
    }

    // EventListener cannot be used here, as the bean is lazy
    // https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation
    @PostConstruct
    public void scheduleCleanup() {
        // Schedule the cleanup of orphaned sandbox containers 30 seconds after the application has started and then every sandboxCleanupScheduleMinutes minutes.
        taskScheduler.scheduleAtFixedRate(this::reapOrphanedSessions, Instant.now().plusSeconds(30), Duration.ofMinutes(sandboxCleanupScheduleMinutes));
    }

    /**
     * Removes all sandbox containers that are older than the configured expiry threshold. Containers younger than the threshold belong to sessions that may still be in progress
     * and are therefore left untouched.
     */
    public void reapOrphanedSessions() {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            log.debug("Docker is not available. Skipping interactive sandbox cleanup.");
            return;
        }

        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        // Current time in seconds, to compare against the container creation time (also in epoch seconds).
        long now = Instant.now().getEpochSecond();
        // Threshold for "orphaned" sandbox containers in seconds.
        long ageThreshold = sandboxContainerExpiryMinutes * 60L;

        List<Container> orphanedSandboxContainers;
        try {
            orphanedSandboxContainers = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(container -> container.getNames() != null && container.getNames().length > 0
                            && container.getNames()[0].startsWith("/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX))
                    .filter(container -> (now - container.getCreated()) > ageThreshold).toList();
        }
        catch (Exception ex) {
            if (DockerUtil.isDockerNotAvailable(ex)) {
                log.debug("Docker is not available. Skipping interactive sandbox cleanup: {}", ex.getMessage());
                return;
            }
            log.error("Error while listing containers for interactive sandbox cleanup: {}", ex.getMessage(), ex);
            return;
        }

        if (orphanedSandboxContainers.isEmpty()) {
            return;
        }
        log.info("Found {} orphaned interactive sandbox containers", orphanedSandboxContainers.size());
        for (Container container : orphanedSandboxContainers) {
            try (final var removeCommand = dockerClient.removeContainerCmd(container.getId()).withForce(true)) {
                removeCommand.exec();
            }
            catch (Exception ex) {
                log.warn("Failed to reap orphaned interactive sandbox container {}: {}", container.getId(), ex.getMessage());
            }
        }
    }
}
