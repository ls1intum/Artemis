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

import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.UserService;
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
        distributedDataAccessService.getDistributedBuildJobQueue().addItemListener(new QueuedBuildJobItemListener(), true);
        distributedDataAccessService.getDistributedProcessingJobs().addEntryListener(new ProcessingBuildJobItemListener(), true);
        distributedDataAccessService.getDistributedBuildAgentInformation().addEntryListener(new BuildAgentListener(), true);
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
            BuildAgentInformation oldValue = event.getOldValue();
            BuildAgentInformation newValue = event.getValue();

            log.debug("Build agent updated: {}", newValue);
            localCIQueueWebsocketService.sendBuildAgentInformationOverWebsocket(newValue.buildAgent().name());
            if (oldValue != null && oldValue.status() != BuildAgentInformation.BuildAgentStatus.SELF_PAUSED
                    && newValue.status() == BuildAgentInformation.BuildAgentStatus.SELF_PAUSED) {
                notifyAdminAboutAgentPausing(newValue);
            }
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
