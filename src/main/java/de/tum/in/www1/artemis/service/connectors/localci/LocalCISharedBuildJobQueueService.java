package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.concurrent.*;

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

    private final FencedLock lock;

    private final int threadPoolSize;

    @Autowired
    public LocalCISharedBuildJobQueueService(HazelcastInstance hazelcastInstance, ExecutorService localCIBuildExecutorService,
            LocalCIBuildJobManagementService localCIBuildJobManagementService, ParticipationRepository participationRepository,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingMessagingService programmingMessagingService,
            ProgrammingExerciseRepository programmingExerciseRepository, int threadPoolSize) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIBuildExecutorService = (ThreadPoolExecutor) localCIBuildExecutorService;
        this.localCIBuildJobManagementService = localCIBuildJobManagementService;
        this.participationRepository = participationRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.processingJobs = this.hazelcastInstance.getMap("processingJobs");
        this.lock = this.hazelcastInstance.getCPSubsystem().getLock("buildJobQueueLock");
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.queue.addItemListener(new BuildJobItemListener(), true);
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Create build job item object and add it to the queue.
     *
     * @param participationId participation id of the build job
     * @param commitHash      commit hash of the build job
     */
    public void addBuildJobInformation(Long participationId, String commitHash) {
        LocalCIBuildJobQueueItem buildJobQueueItem = new LocalCIBuildJobQueueItem(participationId, commitHash, 0);
        queue.add(buildJobQueueItem);
    }

    /**
     * Get first build job item from the queue. If it exists, process build job and after completion,
     * try to process next item.
     */
    public void processBuild() {

        if (queue.isEmpty()) {
            return;
        }
        // need to add the build job to processingJobs before taking it from the queue,
        // so it can be later added back to the queue if the node fails
        LocalCIBuildJobQueueItem buildJob;

        // lock the queue to prevent multiple nodes from processing the same build job
        lock.lock();
        try {
            buildJob = addToProcessingJobs();
        }
        finally {
            lock.unlock();
        }

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
            processBuild();
        });
    }

    /**
     * Requeue timed out build jobs only once. If a build job is still in processedJobs after the expiration time,
     * it might be because the node crashed. Therefore, the build job is added back to the queue.
     */
    @Scheduled(fixedRate = 60000)
    public void requeueTimedOutJobs() {

        lock.lock();
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
            lock.unlock();
        }
    }

    private LocalCIBuildJobQueueItem addToProcessingJobs() {
        LocalCIBuildJobQueueItem buildJob = queue.poll();
        if (buildJob != null) {
            Long participationId = buildJob.getParticipationId();
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

    // Checks whether the node has at least one thread available for a new build job
    // getActiveCount() returns an approximation thus we double check with getQueue().size()
    private Boolean nodeIsAvailable() {
        log.info("Current active threads: " + localCIBuildExecutorService.getActiveCount());
        return localCIBuildExecutorService.getActiveCount() < threadPoolSize && localCIBuildExecutorService.getQueue().size() < 1;
    }

    private class BuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Item added to queue: " + item.getItem());
            if (nodeIsAvailable()) {
                processBuild();
            }
            else {
                log.info("Node has no available threads currently");
            }
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Item removed from queue: " + item.getItem());
        }
    }
}
