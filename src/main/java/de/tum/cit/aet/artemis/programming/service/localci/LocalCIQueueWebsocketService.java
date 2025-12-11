package de.tum.cit.aet.artemis.programming.service.localci;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;

/**
 * This service is responsible for sending build job queue information over websockets.
 * It listens to changes in the build job queue and sends the updated information to the client.
 * NOTE: This service is only active if the profile "localci" and "scheduling" are active. This avoids sending the
 * same information multiple times and thus also avoids unnecessary load on the server.
 */
@Lazy
@Service
@Profile("localci & scheduling")
public class LocalCIQueueWebsocketService {

    private final LocalCIWebsocketMessagingService localCIWebsocketMessagingService;

    private final DistributedDataAccessService distributedDataAccessService;

    /**
     * Instantiates a new Local ci queue websocket service.
     *
     * @param localCIWebsocketMessagingService the local ci build queue websocket service
     */
    public LocalCIQueueWebsocketService(LocalCIWebsocketMessagingService localCIWebsocketMessagingService, DistributedDataAccessService distributedDataAccessService) {
        this.localCIWebsocketMessagingService = localCIWebsocketMessagingService;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Sends queued jobs over websocket. This method is called when a new job is added to the queue or a job is removed from the queue.
     *
     * @param courseId the course id of the programming exercise related to the job
     */
    void sendQueuedJobsOverWebsocket(long courseId) {
        var queuedJobs = removeUnnecessaryInformation(distributedDataAccessService.getQueuedJobs());
        var queuedJobsForCourse = queuedJobs.stream().filter(job -> job.courseId() == courseId).toList();
        localCIWebsocketMessagingService.sendQueuedBuildJobs(queuedJobs);
        localCIWebsocketMessagingService.sendQueuedBuildJobsForCourse(courseId, queuedJobsForCourse);
    }

    /**
     * Sends processing jobs over websocket. This method is called when a new job is added to the processing jobs or a job is removed from the processing jobs.
     *
     * @param courseId the course id of the programming exercise related to the job
     */
    void sendProcessingJobsOverWebsocket(long courseId) {
        var processingJobs = removeUnnecessaryInformation(distributedDataAccessService.getProcessingJobs());
        var processingJobsForCourse = processingJobs.stream().filter(job -> job.courseId() == courseId).toList();
        localCIWebsocketMessagingService.sendRunningBuildJobs(processingJobs);
        localCIWebsocketMessagingService.sendRunningBuildJobsForCourse(courseId, processingJobsForCourse);
    }

    /**
     * Sends build agent information over websocket. This method is called when a new build agent is added or removed.
     *
     * @param agentName the name of the build agent
     */
    void sendBuildAgentInformationOverWebsocket(String agentName) {
        sendBuildAgentSummaryOverWebsocket();
        sendBuildAgentDetailsOverWebsocket(agentName);
    }

    private void sendBuildAgentSummaryOverWebsocket() {
        var buildAgentSummary = removeUnnecessaryInformationFromBuildAgentInformation(distributedDataAccessService.getBuildAgentInformation());
        localCIWebsocketMessagingService.sendBuildAgentSummary(buildAgentSummary);
    }

    private void sendBuildAgentDetailsOverWebsocket(String agentName) {
        distributedDataAccessService.getBuildAgentInformation().stream().filter(agent -> agent.buildAgent().name().equals(agentName)).findFirst()
                .ifPresent(localCIWebsocketMessagingService::sendBuildAgentDetails);
    }

    /**
     * Removes unnecessary information (e.g. repository info, build config, result) from the queued jobs before sending them over the websocket.
     *
     * @param queuedJobs the queued jobs
     */
    private static List<BuildJobQueueItem> removeUnnecessaryInformation(List<BuildJobQueueItem> queuedJobs) {
        var filteredQueuedJobs = new ArrayList<BuildJobQueueItem>(); // make list mutable in case it is not
        for (BuildJobQueueItem job : queuedJobs) {
            var buildConfig = removeUnnecessaryInformationFromBuildConfig(job.buildConfig());
            var repositoryInfo = removeUnnecessaryInformationFromRepositoryInfo(job.repositoryInfo());
            filteredQueuedJobs.add(new BuildJobQueueItem(job.id(), job.name(), job.buildAgent(), job.participationId(), job.courseId(), job.exerciseId(), job.retryCount(),
                    job.priority(), job.status(), repositoryInfo, job.jobTimingInfo(), buildConfig, null, job.containerId()));

        }
        return filteredQueuedJobs;
    }

    /**
     * Removes unnecessary information (e.g. build script, docker image) from the build config before sending it over the websocket.
     *
     * @param buildConfig the build config
     */
    private static BuildConfig removeUnnecessaryInformationFromBuildConfig(BuildConfig buildConfig) {
        // We pass "" instead of null strings to avoid errors when serializing to JSON
        return new BuildConfig("", "", buildConfig.commitHashToBuild(), "", "", "", null, null, buildConfig.scaEnabled(), buildConfig.sequentialTestRunsEnabled(), null,
                buildConfig.timeoutSeconds(), "", "", "", null);
    }

    /**
     * Removes unnecessary information (RepositoryUris) from the repository info before sending it over the websocket.
     *
     * @param repositoryInfo the repository info
     */
    private static RepositoryInfo removeUnnecessaryInformationFromRepositoryInfo(RepositoryInfo repositoryInfo) {
        // We pass "" instead of null strings to avoid errors when serializing to JSON
        return new RepositoryInfo(repositoryInfo.repositoryName(), repositoryInfo.repositoryType(), repositoryInfo.triggeredByPushTo(), "", "", "", null, null);
    }

    /**
     * Removes unnecessary information (e.g. recent build jobs, public ssh key, result) from the running jobs before sending them over the websocket.
     *
     * @param buildAgentSummary the build agent summary
     */
    private static List<BuildAgentInformation> removeUnnecessaryInformationFromBuildAgentInformation(List<BuildAgentInformation> buildAgentSummary) {
        var filteredBuildAgentSummary = new ArrayList<BuildAgentInformation>(); // make list mutable in case it is not
        for (BuildAgentInformation agent : buildAgentSummary) {
            var runningJobs = removeUnnecessaryInformation(agent.runningBuildJobs());
            filteredBuildAgentSummary.add(new BuildAgentInformation(agent.buildAgent(), agent.maxNumberOfConcurrentBuildJobs(), agent.numberOfCurrentBuildJobs(), runningJobs,
                    agent.status(), null, null, agent.pauseAfterConsecutiveBuildFailures()));
        }
        return filteredBuildAgentSummary;
    }

}
