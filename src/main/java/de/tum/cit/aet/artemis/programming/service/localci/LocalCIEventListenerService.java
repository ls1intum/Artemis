package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.ObjectListener;
import org.redisson.api.RMap;
import org.redisson.api.RPriorityQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.ListAddListener;
import org.redisson.api.listener.ListRemoveListener;
import org.redisson.api.map.event.EntryCreatedListener;
import org.redisson.api.map.event.EntryEvent;
import org.redisson.api.map.event.EntryRemovedListener;
import org.redisson.api.map.event.EntryUpdatedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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

    private final RedissonClient redissonClient;

    private final LocalCIQueueWebsocketService localCIQueueWebsocketService;

    private final BuildJobRepository buildJobRepository;

    private final SharedQueueManagementService sharedQueueManagementService;

    private final ProgrammingMessagingService programmingMessagingService;

    public LocalCIEventListenerService(RedissonClient redissonClient, LocalCIQueueWebsocketService localCIQueueWebsocketService, BuildJobRepository buildJobRepository,
            SharedQueueManagementService sharedQueueManagementService, ProgrammingMessagingService programmingMessagingService) {
        this.redissonClient = redissonClient;
        this.localCIQueueWebsocketService = localCIQueueWebsocketService;
        this.buildJobRepository = buildJobRepository;
        this.sharedQueueManagementService = sharedQueueManagementService;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * Add listeners for build job, build agent changes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        RPriorityQueue<BuildJobQueueItem> queue = redissonClient.getPriorityQueue("buildJobQueue");
        RMap<Long, BuildJobQueueItem> processingJobs = redissonClient.getMap("processingJobs");
        RMap<String, BuildAgentInformation> buildAgentInformation = redissonClient.getMap("buildAgentInformation");

        queue.addListener(new QueuedBuildJobItemListener());
        processingJobs.addListener(new ProcessingBuildJobItemListener());
        buildAgentInformation.addListener(new BuildAgentListener());
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

        var queuedJobs = sharedQueueManagementService.getQueuedJobs();
        var processingJobs = sharedQueueManagementService.getProcessingJobIds();

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

    private static class QueuedBuildJobItemListener implements ListAddListener, ListRemoveListener {

        @Override
        public void onListAdd(String name) {
            log.info("Added item to build job queue: {}", name);
            // localCIQueueWebsocketService.sendQueuedJobsOverWebsocket("");
        }

        @Override
        public void onListRemove(String name) {
            log.info("Removed item from build job queue: {}", name);
            // localCIQueueWebsocketService.sendQueuedJobsOverWebsocket(event.getItem().courseId());
        }
    }

    private class ProcessingBuildJobItemListener implements ObjectListener, EntryCreatedListener<Long, BuildJobQueueItem>, EntryRemovedListener<Long, BuildJobQueueItem> {

        @Override
        public void onCreated(EntryEvent<Long, BuildJobQueueItem> entryEvent) {
            BuildJobQueueItem queueItem = entryEvent.getValue();
            log.info("CIBuildJobQueueItem added to processing jobs: {}", queueItem);
            localCIQueueWebsocketService.sendProcessingJobsOverWebsocket(queueItem.courseId());
            buildJobRepository.updateBuildJobStatusWithBuildStartDate(queueItem.id(), BuildStatus.BUILDING, queueItem.jobTimingInfo().buildStartDate());
            notifyUserAboutBuildProcessing(queueItem.exerciseId(), queueItem.participationId(), queueItem.buildConfig().assignmentCommitHash(),
                    queueItem.jobTimingInfo().submissionDate(), queueItem.jobTimingInfo().buildStartDate(), queueItem.jobTimingInfo().estimatedCompletionDate());
        }

        @Override
        public void onRemoved(EntryEvent<Long, BuildJobQueueItem> entryEvent) {
            log.info("CIBuildJobQueueItem removed from processing jobs: {}", entryEvent.getOldValue());
            localCIQueueWebsocketService.sendProcessingJobsOverWebsocket(entryEvent.getOldValue().courseId());
        }
    }

    private class BuildAgentListener implements ObjectListener, EntryCreatedListener<String, BuildAgentInformation>, EntryRemovedListener<String, BuildAgentInformation>,
            EntryUpdatedListener<String, BuildAgentInformation> {

        @Override
        public void onCreated(EntryEvent<String, BuildAgentInformation> entryEvent) {
            log.debug("Build agent added: {}", entryEvent.getValue());
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(entryEvent.getValue().buildAgent().name());
        }

        @Override
        public void onRemoved(EntryEvent<String, BuildAgentInformation> entryEvent) {
            log.debug("Build agent removed: {}", entryEvent.getOldValue());
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(entryEvent.getOldValue().buildAgent().name());
        }

        @Override
        public void onUpdated(EntryEvent<String, BuildAgentInformation> entryEvent) {
            log.debug("Build agent updated: {}", entryEvent.getValue());
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(entryEvent.getValue().buildAgent().name());
        }
    }

    private void notifyUserAboutBuildProcessing(long exerciseId, long participationId, String commitHash, ZonedDateTime submissionDate, ZonedDateTime buildStartDate,
            ZonedDateTime estimatedCompletionDate) {
        var submissionProcessingDTO = new SubmissionProcessingDTO(exerciseId, participationId, commitHash, submissionDate, buildStartDate, estimatedCompletionDate);
        programmingMessagingService.notifyUserAboutSubmissionProcessing(submissionProcessingDTO, exerciseId, participationId);
    }
}
