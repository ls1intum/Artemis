package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDetailsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

@Profile(PROFILE_BUILDAGENT)
@Service
public class BuildAgentInformationService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(BuildAgentInformationService.class);

    private final HazelcastInstance hazelcastInstance;

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildAgentSshKeyService buildAgentSSHKeyService;

    private final GitProperties gitProperties;

    private final DistributedDataAccessService distributedDataAccessService;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.build-agent.display-name:}")
    private String buildAgentDisplayName;

    public BuildAgentInformationService(HazelcastInstance hazelcastInstance, BuildAgentConfiguration buildAgentConfiguration, BuildAgentSshKeyService buildAgentSSHKeyService,
            DistributedDataAccessService distributedDataAccessService, GitProperties gitProperties) {
        this.hazelcastInstance = hazelcastInstance;
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildAgentSSHKeyService = buildAgentSSHKeyService;
        this.gitProperties = gitProperties;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    public void updateLocalBuildAgentInformation(boolean isPaused) {
        updateLocalBuildAgentInformationWithRecentJob(null, isPaused);
    }

    /**
     * Updates the local build agent information with the most recent build job.
     *
     * @param recentBuildJob the most recent build job
     * @param isPaused       whether the build agent is paused
     */
    public void updateLocalBuildAgentInformationWithRecentJob(BuildJobQueueItem recentBuildJob, boolean isPaused) {
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        try {
            distributedDataAccessService.getDistributedBuildAgentInformation().lock(memberAddress);
            // Add/update
            BuildAgentInformation info = getUpdatedLocalBuildAgentInformation(recentBuildJob, isPaused);
            try {
                distributedDataAccessService.getDistributedBuildAgentInformation().put(info.buildAgent().memberAddress(), info);
            }
            catch (Exception e) {
                log.error("Error while updating build agent information for agent {} with address {}", info.buildAgent().name(), info.buildAgent().memberAddress(), e);
            }
        }
        catch (Exception e) {
            log.error("Error while updating build agent information for agent with address {}", memberAddress, e);
        }
        finally {
            distributedDataAccessService.getDistributedBuildAgentInformation().unlock(memberAddress);
        }
    }

    private BuildAgentInformation getUpdatedLocalBuildAgentInformation(BuildJobQueueItem recentBuildJob, boolean isPaused) {
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        List<BuildJobQueueItem> processingJobsOfMember = getProcessingJobsOfNode(memberAddress);
        int numberOfCurrentBuildJobs = processingJobsOfMember.size();
        int maxNumberOfConcurrentBuilds = buildAgentConfiguration.getBuildExecutor() != null ? buildAgentConfiguration.getBuildExecutor().getMaximumPoolSize()
                : buildAgentConfiguration.getThreadPoolSize();
        boolean hasJobs = numberOfCurrentBuildJobs > 0;
        BuildAgentInformation.BuildAgentStatus status = isPaused ? BuildAgentInformation.BuildAgentStatus.PAUSED
                : hasJobs ? BuildAgentInformation.BuildAgentStatus.ACTIVE : BuildAgentInformation.BuildAgentStatus.IDLE;
        BuildAgentInformation agent = distributedDataAccessService.getDistributedBuildAgentInformation().get(memberAddress);

        String publicSshKey = buildAgentSSHKeyService.getPublicKeyAsString();

        BuildAgentDTO agentInfo = new BuildAgentDTO(buildAgentShortName, memberAddress, buildAgentDisplayName);

        BuildAgentDetailsDTO agentDetails = getBuildAgentDetails(agent, recentBuildJob);

        return new BuildAgentInformation(agentInfo, maxNumberOfConcurrentBuilds, numberOfCurrentBuildJobs, processingJobsOfMember, status, publicSshKey, agentDetails);
    }

    private BuildAgentDetailsDTO getBuildAgentDetails(BuildAgentInformation agent, BuildJobQueueItem recentBuildJob) {
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

        return new BuildAgentDetailsDTO(averageBuildDuration, successfulBuilds, failedBuilds, cancelledBuilds, timedOutBuilds, totalsBuilds, lastBuildDate, startDate, gitRevision);
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

    private List<BuildJobQueueItem> getProcessingJobsOfNode(String memberAddress) {
        return distributedDataAccessService.getProcessingJobs().stream().filter(job -> Objects.equals(job.buildAgent().memberAddress(), memberAddress)).toList();
    }
}
