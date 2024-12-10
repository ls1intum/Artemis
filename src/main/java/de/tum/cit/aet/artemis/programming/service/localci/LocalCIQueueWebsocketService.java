package de.tum.cit.aet.artemis.programming.service.localci;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

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
@Service
@Profile("localci & scheduling")
public class LocalCIQueueWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIQueueWebsocketService.class);

    private final LocalCIWebsocketMessagingService localCIWebsocketMessagingService;

    private final SharedQueueManagementService sharedQueueManagementService;

    private final HazelcastInstance hazelcastInstance;

    /**
     * Instantiates a new Local ci queue websocket service.
     *
     * @param hazelcastInstance                the hazelcast instance
     * @param localCIWebsocketMessagingService the local ci build queue websocket service
     * @param sharedQueueManagementService     the local ci shared build job queue service
     */
    public LocalCIQueueWebsocketService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, LocalCIWebsocketMessagingService localCIWebsocketMessagingService,
            SharedQueueManagementService sharedQueueManagementService) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIWebsocketMessagingService = localCIWebsocketMessagingService;
        this.sharedQueueManagementService = sharedQueueManagementService;
    }

    /**
     * Add listeners for build job queue changes.
     */
    @PostConstruct
    public void init() {
        IQueue<BuildJobQueueItem> queue = hazelcastInstance.getQueue("buildJobQueue");
        IMap<Long, BuildJobQueueItem> processingJobs = hazelcastInstance.getMap("processingJobs");
        IMap<String, BuildAgentInformation> buildAgentInformation = hazelcastInstance.getMap("buildAgentInformation");
        queue.addItemListener(new QueuedBuildJobItemListener(), true);
        processingJobs.addEntryListener(new ProcessingBuildJobItemListener(), true);
        buildAgentInformation.addEntryListener(new BuildAgentListener(), true);
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

    private class QueuedBuildJobItemListener implements ItemListener<BuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<BuildJobQueueItem> event) {
            sendQueuedJobsOverWebsocket(event.getItem().courseId());
        }

        @Override
        public void itemRemoved(ItemEvent<BuildJobQueueItem> event) {
            sendQueuedJobsOverWebsocket(event.getItem().courseId());
        }
    }

    private class ProcessingBuildJobItemListener implements EntryAddedListener<Long, BuildJobQueueItem>, EntryRemovedListener<Long, BuildJobQueueItem> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<Long, BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem added to processing jobs: {}", event.getValue());
            sendProcessingJobsOverWebsocket(event.getValue().courseId());
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<Long, BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem removed from processing jobs: {}", event.getOldValue());
            sendProcessingJobsOverWebsocket(event.getOldValue().courseId());
        }
    }

    private class BuildAgentListener
            implements EntryAddedListener<String, BuildAgentInformation>, EntryRemovedListener<String, BuildAgentInformation>, EntryUpdatedListener<String, BuildAgentInformation> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent added: {}", event.getValue());
            sendBuildAgentInformationOverWebsocket(event.getValue().buildAgent().name());
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent removed: {}", event.getOldValue());
            sendBuildAgentInformationOverWebsocket(event.getOldValue().buildAgent().name());
        }

        @Override
        public void entryUpdated(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent updated: {}", event.getValue());
            sendBuildAgentInformationOverWebsocket(event.getValue().buildAgent().name());
        }
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
}
