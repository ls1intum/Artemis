package de.tum.in.www1.artemis.service.connectors.localci;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;

import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildAgentInformation;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.web.websocket.localci.LocalCIWebsocketMessagingService;

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

    private final HazelcastInstance hazelcastInstance;

    private final IQueue<LocalCIBuildJobQueueItem> queue;

    private final IMap<Long, LocalCIBuildJobQueueItem> processingJobs;

    private final IMap<String, LocalCIBuildAgentInformation> buildAgentInformation;

    private final LocalCIWebsocketMessagingService localCIWebsocketMessagingService;

    private final LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService;

    /**
     * Instantiates a new Local ci queue websocket service.
     *
     * @param hazelcastInstance                 the hazelcast instance
     * @param localCIWebsocketMessagingService  the local ci build queue websocket service
     * @param localCISharedBuildJobQueueService the local ci shared build job queue service
     */
    public LocalCIQueueWebsocketService(HazelcastInstance hazelcastInstance, LocalCIWebsocketMessagingService localCIWebsocketMessagingService,
            LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIWebsocketMessagingService = localCIWebsocketMessagingService;
        this.localCISharedBuildJobQueueService = localCISharedBuildJobQueueService;
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.processingJobs = this.hazelcastInstance.getMap("processingJobs");
        this.buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
    }

    /**
     * Add listeners for build job queue changes.
     */
    @PostConstruct
    public void addListeners() {
        this.queue.addItemListener(new QueuedBuildJobItemListener(), true);
        this.processingJobs.addEntryListener(new ProcessingBuildJobItemListener(), true);
        this.buildAgentInformation.addEntryListener(new BuildAgentListener(), true);
    }

    private void sendQueuedJobsOverWebsocket(long courseId) {
        localCIWebsocketMessagingService.sendQueuedBuildJobs(localCISharedBuildJobQueueService.getQueuedJobs());
        localCIWebsocketMessagingService.sendQueuedBuildJobsForCourse(courseId, localCISharedBuildJobQueueService.getQueuedJobsForCourse(courseId));
    }

    private void sendProcessingJobsOverWebsocket(long courseId) {
        localCIWebsocketMessagingService.sendRunningBuildJobs(localCISharedBuildJobQueueService.getProcessingJobs());
        localCIWebsocketMessagingService.sendRunningBuildJobsForCourse(courseId, localCISharedBuildJobQueueService.getProcessingJobsForCourse(courseId));
    }

    private void sendBuildAgentInformationOverWebsocket() {
        localCIWebsocketMessagingService.sendBuildAgentInformation(localCISharedBuildJobQueueService.getBuildAgentInformation());
    }

    private class QueuedBuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> event) {
            sendQueuedJobsOverWebsocket(event.getItem().courseId());
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> event) {
            sendQueuedJobsOverWebsocket(event.getItem().courseId());
        }
    }

    private class ProcessingBuildJobItemListener implements EntryAddedListener<Long, LocalCIBuildJobQueueItem>, EntryRemovedListener<Long, LocalCIBuildJobQueueItem> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<Long, LocalCIBuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem added to processing jobs: {}", event.getValue());
            sendProcessingJobsOverWebsocket(event.getValue().courseId());
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<Long, LocalCIBuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem removed from processing jobs: {}", event.getOldValue());
            sendProcessingJobsOverWebsocket(event.getOldValue().courseId());
        }
    }

    private class BuildAgentListener implements EntryAddedListener<String, LocalCIBuildAgentInformation>, EntryRemovedListener<String, LocalCIBuildAgentInformation> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<String, LocalCIBuildAgentInformation> event) {
            log.debug("Build agent added: {}", event.getValue());
            sendBuildAgentInformationOverWebsocket();
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<String, LocalCIBuildAgentInformation> event) {
            log.debug("Build agent removed: {}", event.getOldValue());
            sendBuildAgentInformationOverWebsocket();
        }
    }
}
