package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.redisson.api.RMap;
import org.redisson.api.RPriorityQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.ListAddListener;
import org.redisson.api.listener.ListRemoveListener;
import org.redisson.api.listener.MapPutListener;
import org.redisson.api.listener.MapRemoveListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.programming.dto.SubmissionProcessingDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;

/**
 * This service is responsible for sending build job queue information over websockets.
 * It listens to changes in the build job queue and sends the updated information to the client.
 * NOTE: This service is only active if the profile "localci" and "scheduling" are active. This avoids sending the
 * same information multiple times and thus also avoids unnecessary load on the server.
 */
@Service
@Profile("localci & scheduling")
public class LocalCIQueueWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIQueueWebsocketService.class);

    private final LocalCIWebsocketMessagingService localCIWebsocketMessagingService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final SharedQueueManagementService sharedQueueManagementService;

    private final RedissonClient redissonClient;

    /**
     * Instantiates a new Local ci queue websocket service.
     *
     * @param redissonClient                   the redissonClient
     * @param localCIWebsocketMessagingService the local ci build queue websocket service
     * @param sharedQueueManagementService     the local ci shared build job queue service
     */
    public LocalCIQueueWebsocketService(RedissonClient redissonClient, LocalCIWebsocketMessagingService localCIWebsocketMessagingService,
            SharedQueueManagementService sharedQueueManagementService, ProgrammingMessagingService programmingMessagingService) {
        this.redissonClient = redissonClient;
        this.localCIWebsocketMessagingService = localCIWebsocketMessagingService;
        this.sharedQueueManagementService = sharedQueueManagementService;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * Add listeners for build job queue changes.
     */
    @PostConstruct
    public void init() {
        RPriorityQueue<BuildJobQueueItem> queue = redissonClient.getPriorityQueue("buildJobQueue");
        RMap<Long, BuildJobQueueItem> processingJobs = redissonClient.getMap("processingJobs");
        RMap<String, BuildAgentInformation> buildAgentInformation = redissonClient.getMap("buildAgentInformation");

        queue.addListener((ListAddListener) item -> {
            log.info("Build job added to queue: {}", item);
            // TODO: how to get the courseId here?
            // TODO: https://redisson.org/docs/data-and-services/collections/#listeners
            long courseId = 1L;
            sendQueuedJobsOverWebsocket(courseId);
        });
        queue.addListener((ListRemoveListener) item -> {
            log.info("Build job removed from queue: {}", item);
            // TODO: how to get the courseId here?
            long courseId = 1L;
            sendQueuedJobsOverWebsocket(courseId);
        });
        processingJobs.addListener((MapPutListener) item -> {
            log.info("Build job added to processing: {}", item);
            // TODO: how to get the courseId here?
            long courseId = 1L;
            sendProcessingJobsOverWebsocket(courseId);
        });
        processingJobs.addListener((MapRemoveListener) item -> {
            log.info("Build job removed to processing: {}", item);
            // TODO: how to get the courseId here?
            long courseId = 1L;
            sendProcessingJobsOverWebsocket(courseId);
        });
        buildAgentInformation.addListener((MapPutListener) item -> {
            log.info("Build Agent added: {}", item);
            // TODO: get build agent name from item
            sendBuildAgentInformationOverWebsocket(item);
        });
        buildAgentInformation.addListener((MapRemoveListener) item -> {
            log.info("Build Agent removed: {}", item);
            // TODO: get build agent name from item
            sendBuildAgentInformationOverWebsocket(item);
        });
        // TODO: remove listener when the application is shut down
    }

    private void sendQueuedJobsOverWebsocket(long courseId) {
        var queuedJobs = removeUnnecessaryInformation(sharedQueueManagementService.getQueuedJobs());
        var queuedJobsForCourse = queuedJobs.stream().filter(job -> job.courseId() == courseId).toList();
        localCIWebsocketMessagingService.sendQueuedBuildJobs(queuedJobs);
        localCIWebsocketMessagingService.sendQueuedBuildJobsForCourse(courseId, queuedJobsForCourse);
    }

    private void sendProcessingJobsOverWebsocket(long courseId) {
        var processingJobs = removeUnnecessaryInformation(sharedQueueManagementService.getProcessingJobs());
        var processingJobsForCourse = processingJobs.stream().filter(job -> job.courseId() == courseId).toList();
        localCIWebsocketMessagingService.sendRunningBuildJobs(processingJobs);
        localCIWebsocketMessagingService.sendRunningBuildJobsForCourse(courseId, processingJobsForCourse);
    }

    private void sendBuildAgentSummaryOverWebsocket() {
        var buildAgentSummary = removeUnnecessaryInformationFromBuildAgentInformation(sharedQueueManagementService.getBuildAgentInformationWithoutRecentBuildJobs());
        localCIWebsocketMessagingService.sendBuildAgentSummary(buildAgentSummary);
    }

    private void sendBuildAgentDetailsOverWebsocket(String agentName) {
        sharedQueueManagementService.getBuildAgentInformation().stream().filter(agent -> agent.buildAgent().name().equals(agentName)).findFirst()
                .ifPresent(localCIWebsocketMessagingService::sendBuildAgentDetails);
    }

    private void sendBuildAgentInformationOverWebsocket(String agentName) {
        sendBuildAgentSummaryOverWebsocket();
        sendBuildAgentDetailsOverWebsocket(agentName);
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
                    job.priority(), job.status(), repositoryInfo, job.jobTimingInfo(), buildConfig, null));

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
                    agent.status(), null, null));
        }
        return filteredBuildAgentSummary;
    }

    private void notifyUserAboutBuildProcessing(long exerciseId, long participationId, String commitHash, ZonedDateTime submissionDate, ZonedDateTime buildStartDate,
            ZonedDateTime estimatedCompletionDate) {
        var submissionProcessingDTO = new SubmissionProcessingDTO(exerciseId, participationId, commitHash, submissionDate, buildStartDate, estimatedCompletionDate);
        programmingMessagingService.notifyUserAboutSubmissionProcessing(submissionProcessingDTO, exerciseId, participationId);
    }
}
