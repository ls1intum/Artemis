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
 * Central event listener and recovery coordinator for the LocalCI subsystem.
 *
 * <p>
 * <strong>Overview</strong>
 * </p>
 * This service wires together all Hazelcast-based event listeners relevant to LocalCI operation.
 * It ensures that build job states, build agent information, and result processing remain consistent
 * across the distributed cluster — even under transient network issues, node restarts, or CI agent failures.
 *
 * <p>
 * <strong>Responsibilities</strong>
 * </p>
 * <ul>
 * <li>Registers distributed listeners for:
 * <ul>
 * <li>Queued build jobs — updates user-facing WebSocket state when jobs are added or removed.</li>
 * <li>Processing build jobs — updates status, timestamps, and triggers user notifications when builds start or complete.</li>
 * <li>Build agent information — tracks agent availability and notifies administrators when an agent pauses itself after repeated failures.</li>
 * </ul>
 * </li>
 * <li>Provides a periodic safety task that reprocesses any unhandled build results still present in the distributed queue
 * (e.g., if transient network load or node interruptions caused missed listener events).</li>
 * <li>Ensures real-time feedback through WebSocket broadcasts to instructors and students.</li>
 * </ul>
 *
 * <p>
 * <strong>Fault Tolerance and Recovery</strong>
 * </p>
 * <ul>
 * <li>Detects and marks "lost" or "stuck" jobs that may occur after build agent crashes or Hazelcast disconnects.</li>
 * <li>Processes leftover result queue entries periodically to ensure no build results are stranded.</li>
 * <li>Sends targeted email alerts when a build agent self-pauses due to consecutive failures.</li>
 * </ul>
 *
 * <p>
 * <strong>Concurrency & Lifecycle</strong>
 * </p>
 * <ul>
 * <li>All Hazelcast listeners run asynchronously and must remain lightweight.</li>
 * <li>The scheduled fallback mechanism runs every 10 seconds to minimize perceived delays.</li>
 * <li>Listeners are registered during {@link PostConstruct} initialization — {@code @EventListener} cannot be used
 * since the bean is {@code @Lazy} and operates under the "localci & scheduling" profile.</li>
 * </ul>
 *
 * <p>
 * <strong>Extension Guidelines</strong>
 * </p>
 * <ul>
 * <li>Any new LocalCI-related Hazelcast topic, queue, or map should register its listener here to ensure consistent cluster behavior.</li>
 * <li>New event types should integrate both with {@link LocalCIQueueWebsocketService} (for real-time updates)
 * and with persistence or recovery logic as appropriate.</li>
 * </ul>
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
     * This is a fallback mechanism to ensure that no results are left unprocessed in the queue e.g. if listener events are lost under high system load or network hiccups.
     * Runs every 10s so results are not stuck int the queue so long that they appear to be lost.
     */
    // TODO: we should add this on all core nodes, not only on the primary scheduling one
    @Scheduled(fixedRate = 10 * 1000) // every 10 seconds
    public void processQueuedResults() {
        final int resultQueueSize = distributedDataAccessService.getResultQueueSize();
        if (resultQueueSize > 0) {
            log.info("Scheduled task found {} queued results in the Hazelcast distributed build result queue. Will process these results now.", resultQueueSize);
            for (int i = 0; i < resultQueueSize; i++) {
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
    }

    /**
     * Listener for the distributed *queued build jobs* collection.
     *
     * <p>
     * <strong>Purpose</strong>: Push real-time queue size/state updates to clients
     * when items are added or removed.
     * </p>
     *
     * <p>
     * <strong>Threading</strong>: Keep callbacks lightweight; heavy work is handled
     * elsewhere. Safe to call WebSocket updates directly.
     * </p>
     *
     * @see LocalCIQueueWebsocketService#sendQueuedJobsOverWebsocket(long)
     */
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

    /**
     * Listener for the distributed *processing jobs* map.
     *
     * <p>
     * <strong>Purpose</strong>:
     * <ul>
     * <li>When a job transitions to processing, update UI and persist status/timestamps.</li>
     * <li>When a job leaves processing, update UI accordingly.</li>
     * </ul>
     * </p>
     *
     * <p>
     * <strong>Side effects</strong>:
     * <ul>
     * <li>WebSocket broadcast to clients for the affected course.</li>
     * <li>Persist job status to {@code BUILDING} with start time.</li>
     * <li>User notification that the submission is being processed.</li>
     * </ul>
     * </p>
     *
     * <p>
     * <strong>Threading</strong>: Keep operations brief; no blocking or long-running work.
     * </p>
     *
     * @see BuildJobRepository#updateBuildJobStatusWithBuildStartDate(String, BuildStatus, java.time.ZonedDateTime)
     * @see LocalCIQueueWebsocketService#sendProcessingJobsOverWebsocket(long)
     * @see ProgrammingMessagingService#notifyUserAboutSubmissionProcessing(SubmissionProcessingDTO, long, long)
     */
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

    /**
     * Listener for the distributed *build agent information* map.
     *
     * <p>
     * <strong>Purpose</strong>:
     * <ul>
     * <li>Broadcast agent presence and status changes to the UI.</li>
     * <li>Notify administrators when an agent self-pauses due to consecutive failures.</li>
     * </ul>
     * </p>
     *
     * <p>
     * <strong>Behavior</strong>:
     * <ul>
     * <li>{@code entryAdded}/{@code entryRemoved}: push current agent roster/status.</li>
     * <li>{@code entryUpdated}: push updated status; if transition → {@code SELF_PAUSED}, notify admin.</li>
     * </ul>
     * </p>
     *
     * <p>
     * <strong>Threading</strong>: Work is lightweight (broadcast + optional email enqueue).
     * </p>
     *
     * @see LocalCIQueueWebsocketService#sendBuildAgentInformationOverWebsocket(String)
     * @see LocalCIEventListenerService#notifyAdminAboutAgentPausing(BuildAgentInformation)
     */
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
