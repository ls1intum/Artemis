package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final IMap<Long, LocalCIBuildJobQueueItem> processingJobs;

    // lock to prevent multiple nodes from processing the same build job
    private final FencedLock fLock;

    // lock for operations on single instance
    private final ReentrantLock lock = new ReentrantLock();

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
        this.fLock = this.hazelcastInstance.getCPSubsystem().getLock("buildJobQueueLock");
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
     * Get first build job item from the queue. If it exists, process build job and after completion,
     * try to process next item.
     */
    private void processBuild(LocalCIBuildJobQueueItem buildJob) {

        if (buildJob == null) {
            return;
        }

        log.info("Processing build job: " + buildJob);

        String commitHash = buildJob.getCommitHash();
        // participation might not be persisted in the database yet
        ProgrammingExerciseParticipation participation = retrieveParticipationWithRetry(buildJob.getParticipationId());

        // For some reason, it is possible that the participation object does not have the programming exercise
        if (participation.getProgrammingExercise() == null) {
            participation.setProgrammingExercise(programmingExerciseRepository.findByParticipationIdOrElseThrow(participation.getId()));
        }

        CompletableFuture<LocalCIBuildResult> futureResult = localCIBuildJobManagementService.addBuildJobToQueue(participation, commitHash);
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

            // process next build job
            processNextBuildJob();
        });
    }

    /**
     * Requeue timed out build jobs only once. If a build job is still in processedJobs after the expiration time,
     * it might be because the node crashed. Therefore, the build job is added back to the queue.
     */
    @Scheduled(fixedRate = 60000)
    protected void requeueTimedOutJobs() {

        fLock.lock();
        try {
            for (Long participationId : processingJobs.keySet()) {
                LocalCIBuildJobQueueItem buildJob = processingJobs.get(participationId);
                if (buildJob != null && buildJob.getExpirationTime() < System.currentTimeMillis()) {
                    if (buildJob.getRetryCount() > 0) {
                        log.error("Build job timed out for the second time: " + buildJob + ". Removing it from the queue.");
                        processingJobs.delete(participationId);
                        continue;
                    }
                    log.warn("Requeueing timed out build job: " + buildJob);
                    processingJobs.delete(participationId);
                    buildJob.setRetryCount(buildJob.getRetryCount() + 1);
                    queue.add(buildJob);
                }
            }
        }
        finally {
            fLock.unlock();
        }
    }

    private LocalCIBuildJobQueueItem addToProcessingJobs() {
        LocalCIBuildJobQueueItem buildJob = queue.poll();
        if (buildJob != null) {
            Long participationId = buildJob.getParticipationId();
            buildJob.setBuildStartDate(System.currentTimeMillis());
            buildJob.setExpirationTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(180));
            processingJobs.put(participationId, buildJob);
        }
        return buildJob;
    }

    private ProgrammingExerciseParticipation retrieveParticipationWithRetry(Long participationId) {
        int maxRetries = 5;
        int retries = 0;
        while (retries < maxRetries) {
            try {
                return (ProgrammingExerciseParticipation) participationRepository.findByIdElseThrow(participationId);
            }
            catch (Exception e) {
                log.debug("Error while retrieving participation with id " + participationId + " from database: " + e.getMessage());
                log.info("Retrying to retrieve participation with id " + participationId + " from database");
                retries++;
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e1) {
                    log.error("Error while waiting for participation with id " + participationId + " to be persisted in database: " + e1.getMessage());
                }
            }
        }
        throw new IllegalStateException("Could not retrieve participation with id " + participationId + " from database after " + maxRetries + " retries");
    }

    private void processNextBuildJob() {
        lock.lock();

        if (!nodeIsAvailable()) {
            log.info("Node has no available threads currently");
            return;
        }

        if (queue.isEmpty()) {
            return;
        }
        // need to add the build job to processingJobs before taking it from the queue,
        // so it can be later added back to the queue if the node fails
        LocalCIBuildJobQueueItem buildJob;

        // lock the queue to prevent multiple nodes from processing the same build job
        fLock.lock();
        try {
            buildJob = addToProcessingJobs();
        }
        finally {
            fLock.unlock();
        }

        lock.unlock();
        processBuild(buildJob);
    }

    // Checks whether the node has at least one thread available for a new build job
    private Boolean nodeIsAvailable() {
        log.info("Current active threads: " + localCIBuildExecutorService.getActiveCount());
        return processingJobs.size() < localCIBuildExecutorService.getMaximumPoolSize();
    }

    private class BuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Item added to queue: " + item.getItem());
            processNextBuildJob();
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Item removed from queue: " + item.getItem());
        }
    }
}
