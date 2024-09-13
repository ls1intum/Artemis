package de.tum.cit.aet.artemis.programming.service.localci;

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
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;

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
            SharedQueueManagementService sharedQueueManagementService) {
        this.redissonClient = redissonClient;
        this.localCIWebsocketMessagingService = localCIWebsocketMessagingService;
        this.sharedQueueManagementService = sharedQueueManagementService;
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
        localCIWebsocketMessagingService.sendQueuedBuildJobs(sharedQueueManagementService.getQueuedJobs());
        localCIWebsocketMessagingService.sendQueuedBuildJobsForCourse(courseId, sharedQueueManagementService.getQueuedJobsForCourse(courseId));
    }

    private void sendProcessingJobsOverWebsocket(long courseId) {
        localCIWebsocketMessagingService.sendRunningBuildJobs(sharedQueueManagementService.getProcessingJobs());
        localCIWebsocketMessagingService.sendRunningBuildJobsForCourse(courseId, sharedQueueManagementService.getProcessingJobsForCourse(courseId));
    }

    private void sendBuildAgentSummaryOverWebsocket() {
        // remove the recentBuildJobs from the build agent information before sending it over the websocket
        List<BuildAgentInformation> buildAgentSummary = sharedQueueManagementService.getBuildAgentInformationWithoutRecentBuildJobs();
        localCIWebsocketMessagingService.sendBuildAgentSummary(buildAgentSummary);
    }

    private void sendBuildAgentDetailsOverWebsocket(String agentName) {
        sharedQueueManagementService.getBuildAgentInformation().stream().filter(agent -> agent.name().equals(agentName)).findFirst()
                .ifPresent(localCIWebsocketMessagingService::sendBuildAgentDetails);
    }

    private void sendBuildAgentInformationOverWebsocket(String agentName) {
        sendBuildAgentSummaryOverWebsocket();
        sendBuildAgentDetailsOverWebsocket(agentName);
    }
}
