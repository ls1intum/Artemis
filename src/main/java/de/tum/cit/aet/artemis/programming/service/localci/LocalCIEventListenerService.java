package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.UserService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.SubmissionProcessingDTO;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;

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
@Lazy
@Service
@Profile("localci & scheduling")
public class LocalCIEventListenerService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIEventListenerService.class);

    private final LocalCIQueueWebsocketService localCIQueueWebsocketService;

    private final BuildJobRepository buildJobRepository;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final LocalCIResultProcessingService localCIResultProcessingService;

    private final UserService userService;

    private final MailService mailService;

    public LocalCIEventListenerService(DistributedDataAccessService distributedDataAccessService, LocalCIQueueWebsocketService localCIQueueWebsocketService,
            BuildJobRepository buildJobRepository, ProgrammingMessagingService programmingMessagingService, LocalCIResultProcessingService localCIResultProcessingService,
            UserService userService, MailService mailService) {
        this.distributedDataAccessService = distributedDataAccessService;
        this.localCIQueueWebsocketService = localCIQueueWebsocketService;
        this.buildJobRepository = buildJobRepository;
        this.programmingMessagingService = programmingMessagingService;
        this.localCIResultProcessingService = localCIResultProcessingService;
        this.userService = userService;
        this.mailService = mailService;
    }

    /**
     * Add listeners for build job, build agent changes.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        distributedDataAccessService.getDistributedBuildJobQueue().addItemListener(new QueuedBuildJobItemListener());
        distributedDataAccessService.getDistributedProcessingJobs().addEntryListener(new ProcessingBuildJobItemListener());
        distributedDataAccessService.getDistributedBuildAgentInformation().addEntryListener(new BuildAgentListener());
        distributedDataAccessService.getDistributedDockerImageCleanupInfo().addListener(new DockerImageCleanupInfoListener());
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
        log.debug("Checking pending build jobs status");
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

    /**
     * Processes the queued results from the distributed build result queue every minute.
     * This is a fallback mechanism to ensure that no results are left unprocessed in the queue e.g. if listener events are lost
     * under high system load or network hiccups.
     * Runs every minute so results are not stuck int the queue so long that they appear to be lost.
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void processQueuedResults() {
        final int initialSize = distributedDataAccessService.getResultQueueSize();
        log.debug("{} queued results in the distributed build result queue. Processing up to {} results.", initialSize, initialSize);
        for (int i = 0; i < initialSize; i++) {
            if (distributedDataAccessService.getDistributedBuildResultQueue().peek() == null) {
                break;
            }
            try {
                localCIResultProcessingService.processResultAsync();
            }
            catch (Exception ex) {
                log.warn("Processing a queued result failed. Continuing with remaining items", ex);
            }
        }
    }

    private boolean checkIfBuildJobIsStillBuilding(List<String> processingJobIds, String buildJobId) {
        return processingJobIds.contains(buildJobId);
    }

    private boolean checkIfBuildJobIsStillQueued(List<BuildJobQueueItem> queuedJobs, String buildJobId) {
        return queuedJobs.stream().anyMatch(job -> job.id().equals(buildJobId));
    }

    private class QueuedBuildJobItemListener implements QueueItemListener<BuildJobQueueItem> {

        @Override
        public void itemAdded(BuildJobQueueItem item) {
            localCIQueueWebsocketService.sendQueuedJobsOverWebsocket(item.courseId());
        }

        @Override
        public void itemRemoved(BuildJobQueueItem item) {
            localCIQueueWebsocketService.sendQueuedJobsOverWebsocket(item.courseId());
        }
    }

    private class ProcessingBuildJobItemListener implements MapEntryListener<String, BuildJobQueueItem> {

        @Override
        public void entryAdded(MapEntryAddedEvent<String, BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem added to processing jobs: {}", event.value());
            localCIQueueWebsocketService.sendProcessingJobsOverWebsocket(event.value().courseId());
            buildJobRepository.updateBuildJobStatusWithBuildStartDate(event.value().id(), BuildStatus.BUILDING, event.value().jobTimingInfo().buildStartDate());
            notifyUserAboutBuildProcessing(event.value().exerciseId(), event.value().participationId(), event.value().buildConfig().assignmentCommitHash(),
                    event.value().jobTimingInfo().submissionDate(), event.value().jobTimingInfo().buildStartDate(), event.value().jobTimingInfo().estimatedCompletionDate());
        }

        @Override
        public void entryRemoved(MapEntryRemovedEvent<String, BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem removed from processing jobs: {}", event.oldValue());
            localCIQueueWebsocketService.sendProcessingJobsOverWebsocket(event.oldValue().courseId());
        }

        @Override
        public void entryUpdated(MapEntryUpdatedEvent<String, BuildJobQueueItem> event) {
        }
    }

    private class BuildAgentListener implements MapEntryListener<String, BuildAgentInformation> {

        @Override
        public void entryAdded(MapEntryAddedEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent added: {}", event.value());
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(event.value().buildAgent().name());
        }

        @Override
        public void entryRemoved(MapEntryRemovedEvent<String, BuildAgentInformation> event) {
            BuildAgentInformation oldValue = event.oldValue();
            log.debug("Build agent removed: {}", oldValue);
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(oldValue.buildAgent().name());
        }

        @Override
        public void entryUpdated(MapEntryUpdatedEvent<String, BuildAgentInformation> event) {
            BuildAgentInformation oldValue = event.oldValue();
            BuildAgentInformation newValue = event.value();
            log.debug("Build agent updated: {}", newValue);
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(newValue.buildAgent().name());

            if (oldValue != null && oldValue.status() != BuildAgentInformation.BuildAgentStatus.SELF_PAUSED
                    && newValue.status() == BuildAgentInformation.BuildAgentStatus.SELF_PAUSED) {
                notifyAdminAboutAgentPausing(newValue);
            }
        }
    }

    private static class DockerImageCleanupInfoListener implements MapListener {

        @Override
        public void entryAdded() {
            log.debug("Docker image cleanup info added");
        }

        @Override
        public void entryRemoved() {
            log.debug("Docker image cleanup info removed");
        }

        @Override
        public void entryUpdated() {
            log.debug("Docker image cleanup info updated");
        }
    }

    private void notifyAdminAboutAgentPausing(BuildAgentInformation buildAgentInformation) {
        Optional<User> admin = userService.findInternalAdminUser();
        if (admin.isEmpty()) {
            log.warn("No internal admin user found. Cannot notify admin about self pausing build agent.");
            return;
        }
        int failures = buildAgentInformation.buildAgentDetails() != null ? buildAgentInformation.buildAgentDetails().consecutiveBuildFailures()
                : buildAgentInformation.pauseAfterConsecutiveBuildFailures();
        mailService.sendBuildAgentSelfPausedEmailToAdmin(admin.get(), buildAgentInformation.buildAgent().name(), failures);
    }

    private void notifyUserAboutBuildProcessing(long exerciseId, long participationId, String commitHash, ZonedDateTime submissionDate, ZonedDateTime buildStartDate,
            ZonedDateTime estimatedCompletionDate) {
        var submissionProcessingDTO = new SubmissionProcessingDTO(exerciseId, participationId, commitHash, submissionDate, buildStartDate, estimatedCompletionDate);
        programmingMessagingService.notifyUserAboutSubmissionProcessing(submissionProcessingDTO, exerciseId, participationId);
    }
}
