package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.Objects;

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
import de.tum.in.www1.artemis.web.websocket.localci.LocalCIBuildQueueWebsocketService;

/**
 * This service is responsible for sending build job queue information over websockets.
 * It listens to changes in the build job queue and sends the updated information to the client.
 * NOTE: This service is only active if the profile "localci" and "scheduling" are active.
 */
@Service
@Profile("localci & scheduling")
public class LocalCIQueueWebsocketService {

    private final Logger log = LoggerFactory.getLogger(LocalCIQueueWebsocketService.class);

    private final HazelcastInstance hazelcastInstance;

    private final IQueue<LocalCIBuildJobQueueItem> queue;

    private final IMap<Long, LocalCIBuildJobQueueItem> processingJobs;

    private final IMap<String, LocalCIBuildAgentInformation> buildAgentInformation;

    private final LocalCIBuildQueueWebsocketService localCIBuildQueueWebsocketService;

    private final LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService;

    /**
     * Instantiates a new Local ci queue websocket service.
     *
     * @param hazelcastInstance                 the hazelcast instance
     * @param localCIBuildQueueWebsocketService the local ci build queue websocket service
     * @param localCISharedBuildJobQueueService the local ci shared build job queue service
     */
    public LocalCIQueueWebsocketService(HazelcastInstance hazelcastInstance, LocalCIBuildQueueWebsocketService localCIBuildQueueWebsocketService,
            LocalCISharedBuildJobQueueService localCISharedBuildJobQueueService) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIBuildQueueWebsocketService = localCIBuildQueueWebsocketService;
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
        this.processingJobs.addLocalEntryListener(new ProcessingBuildJobItemListener());
        this.buildAgentInformation.addLocalEntryListener(new BuildAgentListener());
        // localCIBuildQueueWebsocketService will be autowired only if scheduling is active
        Objects.requireNonNull(localCIBuildQueueWebsocketService, "localCIBuildQueueWebsocketService must be non-null when scheduling is active.");
    }

    private void sendQueuedJobsOverWebsocket(long courseId) {
        localCIBuildQueueWebsocketService.sendQueuedBuildJobs(localCISharedBuildJobQueueService.getQueuedJobs());
        localCIBuildQueueWebsocketService.sendQueuedBuildJobsForCourse(courseId, localCISharedBuildJobQueueService.getQueuedJobsForCourse(courseId));
    }

    private void sendProcessingJobsOverWebsocket(long courseId) {
        localCIBuildQueueWebsocketService.sendRunningBuildJobs(localCISharedBuildJobQueueService.getProcessingJobs());
        localCIBuildQueueWebsocketService.sendRunningBuildJobsForCourse(courseId, localCISharedBuildJobQueueService.getProcessingJobsForCourse(courseId));
    }

    private void sendBuildAgentInformationOverWebsocket() {
        localCIBuildQueueWebsocketService.sendBuildAgentInformation(localCISharedBuildJobQueueService.getBuildAgentInformation());
    }

    private class QueuedBuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> event) {
            sendQueuedJobsOverWebsocket(event.getItem().getCourseId());
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> event) {
            sendQueuedJobsOverWebsocket(event.getItem().getCourseId());
        }
    }

    private class ProcessingBuildJobItemListener implements EntryAddedListener<Long, LocalCIBuildJobQueueItem>, EntryRemovedListener<Long, LocalCIBuildJobQueueItem> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<Long, LocalCIBuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem added to processing jobs: {}", event.getValue());
            sendProcessingJobsOverWebsocket(event.getValue().getCourseId());
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<Long, LocalCIBuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem removed from processing jobs: {}", event.getOldValue());
            sendProcessingJobsOverWebsocket(event.getOldValue().getCourseId());
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
