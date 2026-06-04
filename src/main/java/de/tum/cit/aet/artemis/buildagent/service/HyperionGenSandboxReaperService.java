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
 * Reaps orphaned interactive generation sandbox containers (those named with the {@link InteractiveSandboxService#SANDBOX_CONTAINER_PREFIX} prefix).
 * <p>
 * This is intentionally a dedicated reaper, separate from the CI build-container cleanup: generation sessions legitimately run for several minutes (far longer than a CI build),
 * so they need a longer expiry threshold, and the CI reaper's prefix never matches a live generation container. A container is only removed once it is older than the
 * configured threshold, so a session in progress is never killed; on agent restart the first sweep removes any session container left behind by a previous process.
 */
@Lazy(false)
@Service
@Profile(PROFILE_BUILDAGENT)
public class HyperionGenSandboxReaperService {

    private static final Logger log = LoggerFactory.getLogger(HyperionGenSandboxReaperService.class);

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final TaskScheduler taskScheduler;

    // Containers older than this (by creation time) are considered orphaned. It must exceed the maximum wall-clock of a live session (agent turns plus the two verification
    // builds) so an in-progress session is never reaped; the default is set comfortably above that worst case.
    @Value("${artemis.continuous-integration.build-agent.generation-container-expiry-minutes:90}")
    private int generationContainerExpiryMinutes;

    @Value("${artemis.continuous-integration.build-agent.generation-cleanup-schedule-minutes:15}")
    private int generationCleanupScheduleMinutes;

    public HyperionGenSandboxReaperService(BuildAgentConfiguration buildAgentConfiguration, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void scheduleCleanup() {
        taskScheduler.scheduleAtFixedRate(this::reapOrphanedSessions, Instant.now().plusSeconds(30), Duration.ofMinutes(generationCleanupScheduleMinutes));
    }

    /**
     * Removes generation sandbox containers older than the configured expiry threshold.
     */
    public void reapOrphanedSessions() {
        if (!buildAgentConfiguration.isDockerAvailable()) {
            return;
        }
        DockerClient dockerClient = buildAgentConfiguration.getDockerClient();
        long now = Instant.now().getEpochSecond();
        long ageThresholdSeconds = generationContainerExpiryMinutes * 60L;
        List<Container> orphaned;
        try {
            orphaned = dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                    .filter(container -> container.getNames() != null && container.getNames().length > 0
                            && container.getNames()[0].startsWith("/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX))
                    .filter(container -> (now - container.getCreated()) > ageThresholdSeconds).toList();
        }
        catch (RuntimeException e) {
            log.warn("Failed to list generation sandbox containers for cleanup: {}", e.getMessage());
            return;
        }
        if (orphaned.isEmpty()) {
            return;
        }
        log.info("Reaping {} orphaned generation sandbox container(s)", orphaned.size());
        for (Container container : orphaned) {
            try (var removeCommand = dockerClient.removeContainerCmd(container.getId()).withForce(true)) {
                removeCommand.exec();
            }
            catch (RuntimeException e) {
                log.warn("Failed to reap generation sandbox container {}: {}", container.getId(), e.getMessage());
            }
        }
    }
}
