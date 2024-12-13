package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
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
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;

@Service
@Profile("localci & scheduling")
public class LocalCIEventListenerService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIEventListenerService.class);

    private final HazelcastInstance hazelcastInstance;

    private final LocalCIQueueWebsocketService localCIQueueWebsocketService;

    private final BuildJobRepository buildJobRepository;

    private final SharedQueueManagementService sharedQueueManagementService;

    public LocalCIEventListenerService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, LocalCIQueueWebsocketService localCIQueueWebsocketService,
            BuildJobRepository buildJobRepository, SharedQueueManagementService sharedQueueManagementService) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIQueueWebsocketService = localCIQueueWebsocketService;
        this.buildJobRepository = buildJobRepository;
        this.sharedQueueManagementService = sharedQueueManagementService;
    }

    /**
     * Add listeners for build job, build agent changes.
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

    /**
     * Check the status of pending build jobs. If a build job is missing from the queue, not being built or not finished, update the status to missing.
     * Default interval is 5 minutes. Default delay is 1 minute.
     */
    @Scheduled(fixedRateString = "${artemis.continuous-integration.check-job-status-interval-seconds:300}", initialDelayString = "${artemis.continuous-integration.check-job-status-delay-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void checkPendingBuildJobsStatus() {
        log.info("Checking pending build jobs status");
        List<BuildJob> pendingBuildJobs = buildJobRepository.findAllByBuildStatusIn(List.of(BuildStatus.QUEUED, BuildStatus.BUILDING));
        for (BuildJob buildJob : pendingBuildJobs) {
            // TODO: Add submission date to build job and check if the build job is older than a certain threshold
            if (buildJob.getBuildStatus() == BuildStatus.QUEUED && checkIfBuildJobIsStillQueued(buildJob.getBuildJobId())) {
                log.debug("Build job with id {} is still queued", buildJob.getBuildJobId());
                continue;
            }
            if (checkIfBuildJobIsStillBuilding(buildJob.getBuildJobId())) {
                log.debug("Build job with id {} is still building", buildJob.getBuildJobId());
                continue;
            }
            if (checkIfBuildJobHasFinished(buildJob.getBuildJobId())) {
                log.debug("Build job with id {} has finished", buildJob.getBuildJobId());
                continue;
            }
            log.error("Build job with id {} is in an unknown state", buildJob.getBuildJobId());
            // If the build job is in an unknown state, set it to missing and update the build start date
            buildJobRepository.updateBuildJobStatusWithBuildStartDate(buildJob.getBuildJobId(), BuildStatus.MISSING, ZonedDateTime.now());
        }
    }

    private boolean checkIfBuildJobIsStillBuilding(String buildJobId) {
        return sharedQueueManagementService.getProcessingJobIds().contains(buildJobId);
    }

    private boolean checkIfBuildJobIsStillQueued(String buildJobId) {
        return sharedQueueManagementService.getQueuedJobs().stream().anyMatch(job -> job.id().equals(buildJobId));
    }

    private boolean checkIfBuildJobHasFinished(String buildJobId) {
        var buildJobOpt = buildJobRepository.findByBuildJobId(buildJobId);
        if (buildJobOpt.isEmpty()) {
            log.error("Build job with id {} not found in database", buildJobId);
            return false;
        }
        var buildJob = buildJobOpt.get();
        return buildJob.getBuildStatus() != BuildStatus.QUEUED && buildJob.getBuildStatus() != BuildStatus.BUILDING;
    }

    private class QueuedBuildJobItemListener implements ItemListener<BuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<BuildJobQueueItem> event) {
            localCIQueueWebsocketService.sendQueuedJobsOverWebsocket(event.getItem().courseId());
        }

        @Override
        public void itemRemoved(ItemEvent<BuildJobQueueItem> event) {
            localCIQueueWebsocketService.sendQueuedJobsOverWebsocket(event.getItem().courseId());
        }
    }

    private class ProcessingBuildJobItemListener implements EntryAddedListener<Long, BuildJobQueueItem>, EntryRemovedListener<Long, BuildJobQueueItem> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<Long, BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem added to processing jobs: {}", event.getValue());
            localCIQueueWebsocketService.sendProcessingJobsOverWebsocket(event.getValue().courseId());
            buildJobRepository.updateBuildJobStatusWithBuildStartDate(event.getValue().id(), BuildStatus.BUILDING, event.getValue().jobTimingInfo().buildStartDate());
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<Long, BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem removed from processing jobs: {}", event.getOldValue());
            localCIQueueWebsocketService.sendProcessingJobsOverWebsocket(event.getOldValue().courseId());
        }
    }

    private class BuildAgentListener
            implements EntryAddedListener<String, BuildAgentInformation>, EntryRemovedListener<String, BuildAgentInformation>, EntryUpdatedListener<String, BuildAgentInformation> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent added: {}", event.getValue());
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(event.getValue().buildAgent().name());
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent removed: {}", event.getOldValue());
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(event.getOldValue().buildAgent().name());
        }

        @Override
        public void entryUpdated(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent updated: {}", event.getValue());
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(event.getValue().buildAgent().name());
        }
    }
}
