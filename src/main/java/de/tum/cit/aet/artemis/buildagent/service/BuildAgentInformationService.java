package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDetailsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
@Service
public class BuildAgentInformationService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BuildAgentInformationService.class);

    private static final int DEFAULT_CONSECUTIVE_FAILURES = 0;

    private final BuildAgentConfiguration buildAgentConfiguration;

    /**
     * The Docker version running on this build agent.
     * Updated periodically by {@link #updateDockerVersion()} and included in build agent details.
     * Marked as volatile to ensure visibility across threads since it's updated by the scheduler
     * and read by request handling threads.
     */
    private volatile String dockerVersion;

    private final BuildAgentSshKeyService buildAgentSSHKeyService;

    private final GitProperties gitProperties;

    private final DistributedDataAccessService distributedDataAccessService;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.build-agent.display-name:}")
    private String buildAgentDisplayName;

    public BuildAgentInformationService(BuildAgentConfiguration buildAgentConfiguration, BuildAgentSshKeyService buildAgentSSHKeyService,
            DistributedDataAccessService distributedDataAccessService, GitProperties gitProperties) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildAgentSSHKeyService = buildAgentSSHKeyService;
        this.gitProperties = gitProperties;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Periodically checks Docker availability and retrieves the Docker version from the Docker daemon.
     * <p>
     * This method is scheduled to run:
     * <ul>
     * <li>10 seconds after application startup (to avoid blocking startup)</li>
     * <li>Every 60 seconds thereafter (using fixedDelay to prevent overlap if Docker daemon is slow)</li>
     * </ul>
     * <p>
     * On success, the method marks Docker as available via {@link BuildAgentConfiguration#setDockerAvailable(boolean)}
     * and updates the version if it changed. On failure, Docker is marked as unavailable. State transitions
     * (available → unavailable and vice versa) are logged at WARN/INFO level; repeated failures log at DEBUG.
     */
    @Scheduled(initialDelayString = "10000", fixedDelayString = "60000")
    public void updateDockerVersion() {
        var dockerClient = buildAgentConfiguration.getDockerClient();
        if (dockerClient == null) {
            return;
        }
        boolean wasAvailable = buildAgentConfiguration.isDockerAvailable();
        try {
            String newVersion = dockerClient.versionCmd().exec().getVersion();
            if (!wasAvailable) {
                log.info("Docker is now available (version: {})", newVersion);
                buildAgentConfiguration.setDockerAvailable(true);
                updateLocalBuildAgentInformation(false);
            }
            if (!Objects.equals(newVersion, dockerVersion)) {
                log.info("Docker version: {}", newVersion);
                dockerVersion = newVersion;
                updateLocalBuildAgentInformation(false);
            }
        }
        catch (Exception e) {
            if (wasAvailable) {
                log.warn("Docker is no longer available: {}", e.getMessage());
            }
            else {
                log.debug("Docker is not available: {}", e.getMessage());
            }
            buildAgentConfiguration.setDockerAvailable(false);
        }
    }

    /**
     * Returns the cached Docker version.
     * This version is periodically updated by {@link #updateDockerVersion()}.
     *
     * @return the Docker version string, or null if not yet retrieved or retrieval failed
     */
    public String getDockerVersion() {
        return dockerVersion;
    }

    /**
     * Removes the build agent from the distributed map when the service is being destroyed.
     * This ensures proper cleanup when the build agent shuts down gracefully.
     */
    @PreDestroy
    public void removeLocalBuildAgentInformationOnShutdown() {
        try {
            if (distributedDataAccessService.isInstanceRunning()) {
                log.info("Build agent '{}' is shutting down. Removing from distributed build agent information map.", buildAgentShortName);
                distributedDataAccessService.getDistributedBuildAgentInformation().remove(buildAgentShortName);
                log.info("Successfully removed build agent '{}' from distributed map.", buildAgentShortName);
            }
        }
        catch (Exception e) {
            log.warn("Error while removing build agent information for '{}' during shutdown: {}", buildAgentShortName, e.getMessage());
        }
    }

    public void updateLocalBuildAgentInformation(boolean isPaused) {
        updateLocalBuildAgentInformationWithRecentJob(null, isPaused, false, DEFAULT_CONSECUTIVE_FAILURES);
    }

    public void updateLocalBuildAgentInformation(boolean isPaused, boolean isPausedDueToFailures, int consecutiveFailures) {
        updateLocalBuildAgentInformationWithRecentJob(null, isPaused, isPausedDueToFailures, consecutiveFailures);
    }

    /**
     * Updates the local build agent information with the most recent build job.
     * Uses the build agent's short name as the map key for stable identification,
     * since the Hazelcast member address may change after initial client connection.
     *
     * @param recentBuildJob        the most recent build job
     * @param isPaused              whether the build agent is paused
     * @param isPausedDueToFailures whether the build agent is paused due to consecutive failures
     * @param consecutiveFailures   number of consecutive build failures on the build agent
     */
    public void updateLocalBuildAgentInformationWithRecentJob(BuildJobQueueItem recentBuildJob, boolean isPaused, boolean isPausedDueToFailures, int consecutiveFailures) {
        // Skip if not connected to cluster (happens when build agent starts before core nodes)
        if (!distributedDataAccessService.isConnectedToCluster()) {
            log.debug("Not connected to Hazelcast cluster yet. Skipping build agent information update.");
            return;
        }

        // Guard against missing/blank buildAgentShortName to prevent key collisions or exceptions
        if (buildAgentShortName == null || buildAgentShortName.isBlank()) {
            log.error("Build agent short name is not configured. Cannot update build agent information.");
            return;
        }

        // Use buildAgentShortName as the stable key - memberAddress can change after Hazelcast client connects
        String agentKey = buildAgentShortName;
        try {
            // Acquire lock before inner try block to ensure unlock() in finally only runs if lock() succeeded
            distributedDataAccessService.getDistributedBuildAgentInformation().lock(agentKey);
            try {
                // Add/update
                BuildAgentInformation info = getUpdatedLocalBuildAgentInformation(recentBuildJob, isPaused, isPausedDueToFailures, consecutiveFailures);

                log.debug("Updating build agent info: key='{}', name='{}', memberAddress='{}', displayName='{}'", agentKey, info.buildAgent().name(),
                        info.buildAgent().memberAddress(), info.buildAgent().displayName());

                // Use the agent's short name as key for stable identification
                distributedDataAccessService.getDistributedBuildAgentInformation().put(agentKey, info);
                log.debug("Successfully stored build agent info with key '{}'. Current map size: {}", agentKey,
                        distributedDataAccessService.getDistributedBuildAgentInformation().size());
            }
            catch (Exception e) {
                log.error("Error while updating build agent information for agent {}", agentKey, e);
            }
            finally {
                distributedDataAccessService.getDistributedBuildAgentInformation().unlock(agentKey);
            }
        }
        catch (Exception e) {
            // Lock acquisition failed (e.g., cluster disconnected) - log and continue
            log.error("Failed to acquire lock for build agent information update for agent {}", agentKey, e);
        }
    }

    private BuildAgentInformation getUpdatedLocalBuildAgentInformation(BuildJobQueueItem recentBuildJob, boolean isPaused, boolean isPausedDueToFailures, int consecutiveFailures) {
        String memberAddress = distributedDataAccessService.getLocalMemberAddress();
        // Use buildAgentShortName for filtering instead of memberAddress, because Hazelcast client connections
        // use ephemeral ports that can change, causing memberAddress filtering to fail
        List<BuildJobQueueItem> processingJobsOfMember = getProcessingJobsOfNode(buildAgentShortName);
        int numberOfCurrentBuildJobs = processingJobsOfMember.size();
        int maxNumberOfConcurrentBuilds = buildAgentConfiguration.getBuildExecutor() != null ? buildAgentConfiguration.getBuildExecutor().getMaximumPoolSize()
                : buildAgentConfiguration.getThreadPoolSize();
        boolean hasJobs = numberOfCurrentBuildJobs > 0;
        BuildAgentStatus status;
        // Use buildAgentShortName as key since that's what we use to store the agent info
        BuildAgentInformation agent = distributedDataAccessService.getDistributedBuildAgentInformation().get(buildAgentShortName);
        if (isPaused) {
            boolean isAlreadySelfPaused = agent != null && agent.status() == BuildAgentStatus.SELF_PAUSED;
            status = (isPausedDueToFailures || isAlreadySelfPaused) ? BuildAgentStatus.SELF_PAUSED : BuildAgentStatus.PAUSED;
        }
        else {
            status = hasJobs ? BuildAgentStatus.ACTIVE : BuildAgentStatus.IDLE;
        }
        String publicSshKey = buildAgentSSHKeyService.getPublicKeyAsString();

        BuildAgentDTO agentInfo = new BuildAgentDTO(buildAgentShortName, memberAddress, buildAgentDisplayName);

        BuildAgentDetailsDTO agentDetails = getBuildAgentDetails(agent, recentBuildJob, consecutiveFailures);

        int pauseAfterConsecutiveFailedJobs = buildAgentConfiguration.getPauseAfterConsecutiveFailedJobs();
        return new BuildAgentInformation(agentInfo, maxNumberOfConcurrentBuilds, numberOfCurrentBuildJobs, processingJobsOfMember, status, publicSshKey, agentDetails,
                pauseAfterConsecutiveFailedJobs);
    }

    private BuildAgentDetailsDTO getBuildAgentDetails(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob, int consecutiveFailures) {
        var gitRevision = gitProperties.getShortCommitId();
        var lastBuildDate = getLastBuildDate(agent, recentBuildJob);
        var startDate = getStartDate(agent);
        var currentBuildDuration = getCurrentBuildDuration(recentBuildJob);
        var averageBuildDuration = getAverageBuildDuration(agent, currentBuildDuration);
        var totalsBuilds = getTotalBuilds(agent, recentBuildJob);
        var successfulBuilds = getSuccessfulBuilds(agent, recentBuildJob);
        var failedBuilds = getFailedBuilds(agent, recentBuildJob);
        var cancelledBuilds = getCancelledBuilds(agent, recentBuildJob);
        var timedOutBuilds = getTimedOutBuilds(agent, recentBuildJob);

        return new BuildAgentDetailsDTO(averageBuildDuration, successfulBuilds, failedBuilds, cancelledBuilds, timedOutBuilds, totalsBuilds, lastBuildDate, startDate, gitRevision,
                consecutiveFailures, dockerVersion);
    }

    private ZonedDateTime getLastBuildDate(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob) {
        return recentBuildJob != null ? recentBuildJob.jobTimingInfo().buildStartDate()
                : (agent != null && agent.buildAgentDetails() != null ? agent.buildAgentDetails().lastBuildDate() : null);
    }

    private ZonedDateTime getStartDate(BuildAgentInformation agent) {
        return agent != null && agent.buildAgentDetails() != null ? agent.buildAgentDetails().startDate() : ZonedDateTime.now();
    }

    private long getCurrentBuildDuration(BuildJobQueueItem recentBuildJob) {
        return recentBuildJob != null ? Duration.between(recentBuildJob.jobTimingInfo().buildStartDate(), recentBuildJob.jobTimingInfo().buildCompletionDate()).toSeconds() : 0;
    }

    private long getAverageBuildDuration(BuildAgentInformation agent, long currentBuildDuration) {
        if (agent == null || agent.buildAgentDetails() == null) {
            return currentBuildDuration;
        }
        else if (currentBuildDuration == 0) {
            return agent.buildAgentDetails().averageBuildDuration();
        }
        return (agent.buildAgentDetails().averageBuildDuration() * agent.buildAgentDetails().totalBuilds() + currentBuildDuration) / (agent.buildAgentDetails().totalBuilds() + 1);
    }

    private long getTotalBuilds(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob) {
        return (agent != null && agent.buildAgentDetails() != null ? agent.buildAgentDetails().totalBuilds() : 0) + (recentBuildJob != null ? 1 : 0);
    }

    private long getSuccessfulBuilds(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob) {
        return (agent != null && agent.buildAgentDetails() != null ? agent.buildAgentDetails().successfulBuilds() : 0)
                + (recentBuildJob != null && recentBuildJob.status() == BuildStatus.SUCCESSFUL ? 1 : 0);
    }

    private long getFailedBuilds(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob) {
        return (agent != null && agent.buildAgentDetails() != null ? agent.buildAgentDetails().failedBuilds() : 0)
                + (recentBuildJob != null && recentBuildJob.status() == BuildStatus.FAILED ? 1 : 0);
    }

    private long getCancelledBuilds(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob) {
        return (agent != null && agent.buildAgentDetails() != null ? agent.buildAgentDetails().cancelledBuilds() : 0)
                + (recentBuildJob != null && recentBuildJob.status() == BuildStatus.CANCELLED ? 1 : 0);
    }

    private long getTimedOutBuilds(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob) {
        return (agent != null && agent.buildAgentDetails() != null ? agent.buildAgentDetails().timedOutBuild() : 0)
                + (recentBuildJob != null && recentBuildJob.status() == BuildStatus.TIMEOUT ? 1 : 0);
    }

    /**
     * Gets the processing jobs assigned to a specific build agent by its name.
     * Uses the agent's name (short-name) for filtering instead of memberAddress,
     * because Hazelcast client connections use ephemeral ports that can change.
     *
     * @param agentName the build agent's short name (stable identifier)
     * @return list of build jobs currently being processed by this agent
     */
    private List<BuildJobQueueItem> getProcessingJobsOfNode(String agentName) {
        return distributedDataAccessService.getProcessingJobs().stream().filter(job -> job.buildAgent() != null && Objects.equals(job.buildAgent().name(), agentName)).toList();
    }
}
