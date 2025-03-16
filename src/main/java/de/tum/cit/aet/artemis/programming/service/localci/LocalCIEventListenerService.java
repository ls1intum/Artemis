package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.SubmissionProcessingDTO;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;

/**
 * Central listener service for handling LocalCI events.
 * This service listens for changes in build jobs and build agents, ensuring that job states are updated correctly
 * and notifications are sent to users. It registers event listeners for build job queues and processing jobs,
 * handling transitions such as a job starting or finishing.
 * The service also periodically checks for lost or stuck jobs, marking them as missing if necessary.
 * This helps recover from issues like CI agent crashes, network disruptions, or application restarts
 * that might cause inconsistencies in job tracking. WebSocket updates are triggered to provide real-time
 * feedback to users.
 * New event listeners should be added here to ensure consistent handling of CI-related events.
 */
@Service
@Profile("localci & scheduling")
public class LocalCIEventListenerService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIEventListenerService.class);

    private final LocalCIQueueWebsocketService localCIQueueWebsocketService;

    private final BuildJobRepository buildJobRepository;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProgrammingMessagingService programmingMessagingService;

    public LocalCIEventListenerService(DistributedDataAccessService distributedDataAccessService, LocalCIQueueWebsocketService localCIQueueWebsocketService,
            BuildJobRepository buildJobRepository, ProgrammingMessagingService programmingMessagingService) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.localCIQueueWebsocketService = localCIQueueWebsocketService;
        this.buildJobRepository = buildJobRepository;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * Add listeners for build job, build agent changes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        distributedDataAccessService.getDistributedQueuedJobs().addItemListener(new QueuedBuildJobItemListener(), true);
        distributedDataAccessService.getDistributedProcessingJobs().addEntryListener(new ProcessingBuildJobItemListener(), true);
        distributedDataAccessService.getDistributedBuildAgentInformation().addEntryListener(new BuildAgentListener(), true);
    }

    /**
     * Periodically checks the status of pending build jobs and updates their status if they are missing.
     * <p>
     * This scheduled task ensures that build jobs which are stuck in the QUEUED or BUILDING state for too long
     * are detected and marked as MISSING if their status cannot be verified. This helps prevent indefinite
     * waiting states due to external failures or inconsistencies in the CI system.
     * </p>
     * <p>
     * This mechanism is necessary because build jobs are managed externally, and various failure scenarios
     * can lead to jobs being lost without Artemis being notified:
     * </p>
     * <ul>
     * <li>Application crashes or restarts while build job was queued</li>
     * <li>network issues leading to Hazelcast data loss</li>
     * <li>Build agent crashes or is disconnected</li>
     * </ul>
     */
    @Scheduled(fixedRateString = "${artemis.continuous-integration.check-job-status-interval-seconds:300}", initialDelayString = "${artemis.continuous-integration.check-job-status-delay-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void checkPendingBuildJobsStatus() {
        log.info("Checking pending build jobs status");
        List<BuildJob> pendingBuildJobs = buildJobRepository.findAllByBuildStatusIn(List.of(BuildStatus.QUEUED, BuildStatus.BUILDING));
        ZonedDateTime now = ZonedDateTime.now();
        final int buildJobExpirationInMinutes = 5; // If a build job is older than 5 minutes, and it's status can't be determined, set it to missing

        var queuedJobs = distributedDataAccessService.getQueuedJobs();
        var processingJobs = distributedDataAccessService.getProcessingJobIds();

        for (BuildJob buildJob : pendingBuildJobs) {
            if (buildJob.getBuildSubmissionDate().isAfter(now.minusMinutes(buildJobExpirationInMinutes))) {
                log.debug("Build job with id {} is too recent to check", buildJob.getBuildJobId());
                continue;
            }
            if (buildJob.getBuildStatus() == BuildStatus.QUEUED && checkIfBuildJobIsStillQueued(queuedJobs, buildJob.getBuildJobId())) {
                log.debug("Build job with id {} is still queued", buildJob.getBuildJobId());
                continue;
            }
            if (checkIfBuildJobIsStillBuilding(processingJobs, buildJob.getBuildJobId())) {
                log.debug("Build job with id {} is still building", buildJob.getBuildJobId());
                continue;
            }
            log.error("Build job with id {} is in an unknown state", buildJob.getBuildJobId());
            // If the build job is in an unknown state, set it to missing and update the build start date
            buildJobRepository.updateBuildJobStatus(buildJob.getBuildJobId(), BuildStatus.MISSING);
        }
    }

    private boolean checkIfBuildJobIsStillBuilding(List<String> processingJobIds, String buildJobId) {
        return processingJobIds.contains(buildJobId);
    }

    private boolean checkIfBuildJobIsStillQueued(List<BuildJobQueueItem> queuedJobs, String buildJobId) {
        return queuedJobs.stream().anyMatch(job -> job.id().equals(buildJobId));
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
            notifyUserAboutBuildProcessing(event.getValue().exerciseId(), event.getValue().participationId(), event.getValue().buildConfig().assignmentCommitHash(),
                    event.getValue().jobTimingInfo().submissionDate(), event.getValue().jobTimingInfo().buildStartDate(),
                    event.getValue().jobTimingInfo().estimatedCompletionDate());
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

    private void notifyUserAboutBuildProcessing(long exerciseId, long participationId, String commitHash, ZonedDateTime submissionDate, ZonedDateTime buildStartDate,
            ZonedDateTime estimatedCompletionDate) {
        var submissionProcessingDTO = new SubmissionProcessingDTO(exerciseId, participationId, commitHash, submissionDate, buildStartDate, estimatedCompletionDate);
        programmingMessagingService.notifyUserAboutSubmissionProcessing(submissionProcessingDTO, exerciseId, participationId);
    }
}
