package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Service
@Profile("localci")
public class LocalCISharedBuildJobQueueService {

    private final Logger log = LoggerFactory.getLogger(LocalCISharedBuildJobQueueService.class);

    private final HazelcastInstance hazelcastInstance;

    private final IQueue<LocalCIBuildJobQueueItem> queue;

    private final ThreadPoolExecutor localCIBuildExecutorService;

    private final LocalCIBuildJobManagementService localCIBuildJobManagementService;

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    /**
     * Map of build jobs currently being processed across all nodes
     */
    private final IMap<Long, LocalCIBuildJobQueueItem> processingJobs;

    private AtomicInteger localProcessingJobs = new AtomicInteger(0);

    /**
     * Lock to prevent multiple nodes from processing the same build job.
     */
    private final FencedLock sharedLock;

    /**
     * Lock for operations on single instance.
     */
    private final ReentrantLock instanceLock = new ReentrantLock();

    @Autowired
    public LocalCISharedBuildJobQueueService(HazelcastInstance hazelcastInstance, ExecutorService localCIBuildExecutorService,
            LocalCIBuildJobManagementService localCIBuildJobManagementService, ParticipationRepository participationRepository,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingMessagingService programmingMessagingService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIBuildExecutorService = (ThreadPoolExecutor) localCIBuildExecutorService;
        this.localCIBuildJobManagementService = localCIBuildJobManagementService;
        this.participationRepository = participationRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.processingJobs = this.hazelcastInstance.getMap("processingJobs");
        this.sharedLock = this.hazelcastInstance.getCPSubsystem().getLock("buildJobQueueLock");
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.queue.addItemListener(new BuildJobItemListener(), true);
    }

    /**
     * Create build job item object and add it to the queue.
     *
     * @param name                     name of the build job
     * @param participationId          participation id of the build job
     * @param repositoryTypeOrUsername repository type (if template or solution) or username (if student repository)
     * @param commitHash               commit hash of the build job
     * @param submissionDate           submission date of the build job
     * @param priority                 priority of the build job
     * @param courseId                 course id of the build job
     * @param isPushToTestRepository   defines if the build job is triggered by a push to a test repository
     */
    public void addBuildJob(String name, long participationId, String repositoryTypeOrUsername, String commitHash, long submissionDate, int priority, long courseId,
            boolean isPushToTestRepository) {
        LocalCIBuildJobQueueItem buildJobQueueItem = new LocalCIBuildJobQueueItem(name, participationId, repositoryTypeOrUsername, commitHash, submissionDate, priority, courseId,
                isPushToTestRepository);
        queue.add(buildJobQueueItem);
    }

    public List<LocalCIBuildJobQueueItem> getQueuedJobs() {
        return queue.stream().toList();
    }

    public List<LocalCIBuildJobQueueItem> getProcessingJobs() {
        return processingJobs.values().stream().toList();
    }

    public List<LocalCIBuildJobQueueItem> getQueuedJobsForCourse(long courseId) {
        return queue.stream().filter(job -> job.getCourseId() == courseId).toList();
    }

    public List<LocalCIBuildJobQueueItem> getProcessingJobsForCourse(long courseId) {
        return processingJobs.values().stream().filter(job -> job.getCourseId() == courseId).toList();
    }

    /**
     * Remove all queued build jobs for a participation from the shared build job queue.
     *
     * @param participationId id of the participation
     */
    public void removeQueuedJobsForParticipation(long participationId) {
        List<LocalCIBuildJobQueueItem> toRemove = new ArrayList<>();
        for (LocalCIBuildJobQueueItem job : queue) {
            if (job.getParticipationId() == participationId) {
                toRemove.add(job);
            }
        }
        queue.removeAll(toRemove);
    }

    /**
     * Retrieve participation from database with retries.
     * This is necessary because the participation might not be persisted in the database yet.
     *
     * @param participationId id of the participation
     */
    private ProgrammingExerciseParticipation retrieveParticipationWithRetry(Long participationId) {
        int maxRetries = 5;
        int retries = 0;
        ProgrammingExerciseParticipation participation;
        Optional<Participation> tempParticipation;
        while (retries < maxRetries) {
            tempParticipation = participationRepository.findById(participationId);
            if (tempParticipation.isPresent()) {
                participation = (ProgrammingExerciseParticipation) tempParticipation.get();
                return participation;
            }
            else {
                log.debug("Could not retrieve participation with id {} from database", participationId);
                log.info("Retrying to retrieve participation with id {} from database", participationId);
                retries++;
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e1) {
                    log.error("Error while waiting for participation with id {} to be persisted in database", participationId, e1);
                }
            }
        }
        throw new IllegalStateException("Could not retrieve participation with id " + participationId + " from database after " + maxRetries + " retries.");
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     * If so, process the next build job.
     */
    private void checkAvailabilityAndProcessNextBuild() {
        // Check conditions before acquiring the lock to avoid unnecessary locking
        if (!nodeIsAvailable()) {
            log.info("Node has no available threads currently");
            return;
        }

        if (queue.isEmpty()) {
            return;
        }

        instanceLock.lock();
        try {
            // Recheck conditions after acquiring the lock to ensure they are still valid
            if (!nodeIsAvailable() || queue.isEmpty()) {
                return;
            }

            LocalCIBuildJobQueueItem buildJob;

            // Lock the queue to prevent multiple nodes from processing the same build job
            sharedLock.lock();
            try {
                buildJob = addToProcessingJobs();
            }
            finally {
                sharedLock.unlock();
            }

            processBuild(buildJob);
        }
        finally {
            instanceLock.unlock();
        }
    }

    private LocalCIBuildJobQueueItem addToProcessingJobs() {
        LocalCIBuildJobQueueItem buildJob = queue.poll();
        if (buildJob != null) {
            buildJob.setBuildStartDate(System.currentTimeMillis());
            processingJobs.put(buildJob.getId(), buildJob);
            localProcessingJobs.incrementAndGet();
        }
        return buildJob;
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     */
    private boolean nodeIsAvailable() {
        log.info("Current active threads: {}", localCIBuildExecutorService.getActiveCount());
        return localProcessingJobs.get() < localCIBuildExecutorService.getMaximumPoolSize();
    }

    /**
     * Process a build job by submitting it to the local CI executor service.
     * On completion, check for next job.
     */
    private void processBuild(LocalCIBuildJobQueueItem buildJob) {

        if (buildJob == null) {
            return;
        }

        log.info("Processing build job: " + buildJob);
        String commitHash = buildJob.getCommitHash();
        boolean isRetry = buildJob.getRetryCount() >= 1;

        ProgrammingExerciseParticipation participation;

        // Participation might not be persisted in the database yet or it has been deleted in the meantime
        try {
            participation = retrieveParticipationWithRetry(buildJob.getParticipationId());
        }
        catch (IllegalStateException e) {
            log.error("Cannot process build job for participation with id {} because it could not be retrieved from the database.", buildJob.getParticipationId());
            processingJobs.remove(buildJob.getId());
            localProcessingJobs.decrementAndGet();
            checkAvailabilityAndProcessNextBuild();
            return;
        }

        // For some reason, it is possible that the participation object does not have the programming exercise
        if (participation.getProgrammingExercise() == null) {
            participation.setProgrammingExercise(programmingExerciseRepository.findByParticipationIdOrElseThrow(participation.getId()));
        }

        CompletableFuture<LocalCIBuildResult> futureResult = localCIBuildJobManagementService.executeBuildJob(participation, commitHash, isRetry,
                buildJob.isPushToTestRepository());
        futureResult.thenAccept(buildResult -> {

            // Do not process the result if the participation has been deleted in the meantime
            Optional<Participation> participationOptional = participationRepository.findById(participation.getId());
            if (participationOptional.isPresent()) {
                // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
                // Therefore, a mock auth object has to be created.
                SecurityUtils.setAuthorizationObject();
                Result result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);
                if (result != null) {
                    programmingMessagingService.notifyUserAboutNewResult(result, participation);
                }
                else {
                    programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation,
                            new BuildTriggerWebsocketError("Result could not be processed", participation.getId()));
                }
            }
            else {
                log.warn("Participation with id {} has been deleted. Cancelling the processing of the build result.", participation.getId());
            }

            // after processing a build job, remove it from the processing jobs
            processingJobs.remove(buildJob.getId());
            localProcessingJobs.decrementAndGet();

            // process next build job if node is available
            checkAvailabilityAndProcessNextBuild();
        }).exceptionally(ex -> {
            log.error("Error while processing build job: {}", buildJob, ex);

            processingJobs.remove(buildJob.getId());
            localProcessingJobs.decrementAndGet();

            if (buildJob.getRetryCount() > 0) {
                log.error("Build job failed for the second time: {}", buildJob);
                return null;
            }

            // Do not requeue the build job if the participation has been deleted in the meantime
            Optional<Participation> participationOptional = participationRepository.findById(participation.getId());
            if (participationOptional.isPresent()) {
                log.warn("Requeueing failed build job: {}", buildJob);
                buildJob.setRetryCount(buildJob.getRetryCount() + 1);
                queue.add(buildJob);
            }
            else {
                log.warn("Participation with id {} has been deleted. Cancelling the requeueing of the build job.", participation.getId());
            }
            return null;
        });
    }

    private class BuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("CIBuildJobQueueItem added to queue: {}", item.getItem());
            checkAvailabilityAndProcessNextBuild();
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("CIBuildJobQueueItem removed from queue: {}", item.getItem());
        }
    }
}
