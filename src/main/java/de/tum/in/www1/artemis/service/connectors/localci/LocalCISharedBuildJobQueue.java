package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Service
@Profile("localci")
public class LocalCISharedBuildJobQueue {

    private final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final HazelcastInstance hazelcastInstance;

    private final IQueue<LocalCIBuildJobQueueItem> queue;

    private final ThreadPoolExecutor localCIBuildExecutorService;

    private final LocalCIBuildJobManagementService localCIBuildJobManagementService;

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingMessagingService programmingMessagingService;

    @Value("${artemis.continuous-integration.thread-pool-size:1}")
    int threadPoolSize;

    @Autowired
    public LocalCISharedBuildJobQueue(HazelcastInstance hazelcastInstance, ExecutorService localCIBuildExecutorService,
            LocalCIBuildJobManagementService localCIBuildJobManagementService, ParticipationRepository participationRepository,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingMessagingService programmingMessagingService) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIBuildExecutorService = (ThreadPoolExecutor) localCIBuildExecutorService;
        this.localCIBuildJobManagementService = localCIBuildJobManagementService;
        this.participationRepository = participationRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingMessagingService = programmingMessagingService;
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.queue.addItemListener(new BuildJobItemListener(), true);
    }

    public void addBuildJobInformation(Long participationId, String commitHash) {
        LocalCIBuildJobQueueItem buildJobQueueItem = new LocalCIBuildJobQueueItem(participationId, commitHash);
        queue.add(buildJobQueueItem);
    }

    public void processBuild() {

        if (queue.isEmpty()) {
            return;
        }

        try {
            LocalCIBuildJobQueueItem buildJob = queue.take();
            log.info("Hazelcast, processing build job: " + buildJob);

            String commitHash = buildJob.getCommitHash();
            // participation might not be persisted in the database yet
            ProgrammingExerciseParticipation participation = retrieveParticipationWithRetry(buildJob.getParticipationId());

            // when trigger build is called regularly
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
            });
        }
        catch (InterruptedException e) {
            log.error("Error while processing build job: " + e.getMessage());
        }

        // after processing a build job, check if there are more jobs to process
        processBuild();

    }

    private ProgrammingExerciseParticipation retrieveParticipationWithRetry(Long participationId) {
        int maxRetries = 5;
        int retries = 0;
        while (retries < maxRetries) {
            try {
                return (ProgrammingExerciseParticipation) participationRepository.findByIdElseThrow(participationId);
            }
            catch (Exception e) {
                log.error("Error while retrieving participation with id " + participationId + " from database: " + e.getMessage());
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
    private Boolean nodeIsAvailable() {
        log.info("Current active threads: " + localCIBuildExecutorService.getActiveCount());
        return localCIBuildExecutorService.getActiveCount() < threadPoolSize;
    }

    private class BuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Hazelcast, item added: " + item.getItem());
            if (nodeIsAvailable()) {
                processBuild();
            }
            else {
                log.info("Hazelcast, node is not available");
            }
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> item) {
            log.info("Hazelcast, item removed: " + item.getItem());
        }
    }
}
