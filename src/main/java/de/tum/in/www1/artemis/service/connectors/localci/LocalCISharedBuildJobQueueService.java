package de.tum.in.www1.artemis.service.connectors.localci;

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
     * @param name            name of the build job
     * @param participationId participation id of the build job
     * @param commitHash      commit hash of the build job
     * @param submissionDate  submission date of the build job
     * @param priority        priority of the build job
     * @param courseId        course id of the build job
     */
    public void addBuildJobInformation(String name, long participationId, String commitHash, long submissionDate, int priority, long courseId) {
        LocalCIBuildJobQueueItem buildJobQueueItem = new LocalCIBuildJobQueueItem(name, participationId, commitHash, submissionDate, priority, courseId);
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
        throw new IllegalStateException("Could not retrieve participation with id " + participationId + " from database after " + maxRetries + " retries");
    }

    private void checkAvailabilityAndProcessNextBuild() {
        instanceLock.lock();

        if (!nodeIsAvailable()) {
            log.info("Node has no available threads currently");
            instanceLock.unlock();
            return;
        }

        if (queue.isEmpty()) {
            instanceLock.unlock();
            return;
        }
        // need to add the build job to processingJobs before taking it from the queue,
        // so it can be later added back to the queue if the node fails
        LocalCIBuildJobQueueItem buildJob;

        // lock the queue to prevent multiple nodes from processing the same build job
        sharedLock.lock();
        try {
            buildJob = addToProcessingJobs();
        }
        finally {
            sharedLock.unlock();
        }

        instanceLock.unlock();
        processBuild(buildJob);
    }

    private LocalCIBuildJobQueueItem addToProcessingJobs() {
        LocalCIBuildJobQueueItem buildJob = queue.poll();
        if (buildJob != null) {
            Long participationId = buildJob.getParticipationId();
            buildJob.setBuildStartDate(System.currentTimeMillis());
            buildJob.setExpirationTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(180));
            processingJobs.put(participationId, buildJob);
            localProcessingJobs.incrementAndGet();
        }
        return buildJob;
    }

    // Checks whether the node has at least one thread available for a new build job
    private boolean nodeIsAvailable() {
        log.info("Current active threads: {}", localCIBuildExecutorService.getActiveCount());
        return localProcessingJobs.get() < localCIBuildExecutorService.getMaximumPoolSize();
    }

    /**
     * Process a build job by adding it to the local CI executor service.
     * On completion, check for next job.
     */
    private void processBuild(LocalCIBuildJobQueueItem buildJob) {

        if (buildJob == null) {
            return;
        }

        log.info("Processing build job: " + buildJob);
        String commitHash = buildJob.getCommitHash();
        boolean isRetry = buildJob.getRetryCount() >= 1;

        // participation might not be persisted in the database yet
        ProgrammingExerciseParticipation participation = retrieveParticipationWithRetry(buildJob.getParticipationId());

        // For some reason, it is possible that the participation object does not have the programming exercise
        if (participation.getProgrammingExercise() == null) {
            participation.setProgrammingExercise(programmingExerciseRepository.findByParticipationIdOrElseThrow(participation.getId()));
        }

        CompletableFuture<LocalCIBuildResult> futureResult = localCIBuildJobManagementService.addBuildJobToQueue(participation, commitHash, isRetry);
        futureResult.thenAccept(buildResult -> {
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

            // after processing a build job, remove it from the processing jobs
            processingJobs.remove(buildJob.getParticipationId());
            localProcessingJobs.decrementAndGet();

            // process next build job if node is available
            checkAvailabilityAndProcessNextBuild();
        }).exceptionally(ex -> {
            log.error("Error while processing build job: {}", buildJob, ex);

            processingJobs.remove(buildJob.getParticipationId());
            localProcessingJobs.decrementAndGet();

            if (buildJob.getRetryCount() > 0) {
                log.error("Build job failed for the second time: {}", buildJob);
                return null;
            }
            log.warn("Requeueing failed build job: {}", buildJob);
            buildJob.setRetryCount(buildJob.getRetryCount() + 1);
            queue.add(buildJob);

            return null;
        });
    }

    private class BuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Item added to queue: {}", item.getItem());
            checkAvailabilityAndProcessNextBuild();
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Item removed from queue: {}", item.getItem());
        }
    }
}
