package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.Optional;

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
        log.info("Registering LocalCI event listeners for build job queue, processing jobs, and build agent information.");
        distributedDataAccessService.getDistributedBuildJobQueue().addItemListener(new QueuedBuildJobItemListener());
        distributedDataAccessService.getDistributedProcessingJobs().addEntryListener(new ProcessingBuildJobItemListener());
        distributedDataAccessService.getDistributedBuildAgentInformation().addEntryListener(new BuildAgentListener());
        distributedDataAccessService.getDistributedDockerImageCleanupInfo().addListener(new DockerImageCleanupInfoListener());
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
        log.info("Scheduled task found {} queued results in the Hazelcast distributed build result queue. Will process these results now.", initialSize);
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
