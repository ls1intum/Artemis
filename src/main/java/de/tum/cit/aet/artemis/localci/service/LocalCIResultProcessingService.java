package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;

@Profile(PROFILE_LOCALCI)
@Lazy
@Service
public class LocalCIResultProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIResultProcessingService.class);

    private static final int BUILD_STATISTICS_UPDATE_THRESHOLD = 10;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final BuildJobRepository buildJobRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository;

    private final ParticipationRepository participationRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private final BuildLogEntryService buildLogEntryService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    private final Optional<LocalCIQueueWebsocketService> localCIQueueWebsocketService;

    private final TransactionTemplate transactionTemplate;

    private UUID listenerId;

    private final AtomicLong processedResults = new AtomicLong();

    private final AtomicLong lastProcessedResults = new AtomicLong();

    @Value("${artemis.continuous-integration.concurrent-result-processing-size:16}")
    private int concurrentResultProcessingSize;

    private ThreadPoolExecutor resultProcessingExecutor;

    public LocalCIResultProcessingService(ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingMessagingService programmingMessagingService,
            BuildJobRepository buildJobRepository, ProgrammingExerciseRepository programmingExerciseRepository, ParticipationRepository participationRepository,
            ProgrammingTriggerService programmingTriggerService, BuildLogEntryService buildLogEntryService,
            ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository, DistributedDataAccessService distributedDataAccessService,
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService, Optional<LocalCIQueueWebsocketService> localCIQueueWebsocketService,
            TransactionTemplate transactionTemplate) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationRepository = participationRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingMessagingService = programmingMessagingService;
        this.buildJobRepository = buildJobRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.buildLogEntryService = buildLogEntryService;
        this.programmingExerciseBuildStatisticsRepository = programmingExerciseBuildStatisticsRepository;
        this.distributedDataAccessService = distributedDataAccessService;
        this.programmingSubmissionMessagingService = programmingSubmissionMessagingService;
        this.localCIQueueWebsocketService = localCIQueueWebsocketService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Initializes the result queue, build agent information map and the locks.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        initResultProcessingExecutor();
        log.info("Adding item listener to distributed result queue for LocalCI result processing service");
        this.listenerId = distributedDataAccessService.getDistributedBuildResultQueue().addItemListener(new ResultQueueListener());
    }

    private void initResultProcessingExecutor() {
        ThreadFactory threadFactory = BasicThreadFactory.builder().namingPattern("local-ci-result-%d")
                .uncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in result processing thread {}", t.getName(), e)).build();

        // buffer up to 5000 tasks before rejecting new tasks. Rejections will not lead to loss because the results maintain in the queue but this speeds up
        // result processing under high load so we do not need to wait for the polling schedule if many results are processed very fast.
        resultProcessingExecutor = new ThreadPoolExecutor(concurrentResultProcessingSize, concurrentResultProcessingSize * 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(5000), threadFactory, new ThreadPoolExecutor.AbortPolicy());
        log.info("Initialized LocalCI result processing executor with pool size {}", concurrentResultProcessingSize);
    }

    /**
     * Logs the health of the result processor every 5 minutes.
     * If there are items in the Hazelcast queue but no results have been processed since the last check, an error is logged.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void logResultProcessorHealth() {
        int hazelcastQueueSize = distributedDataAccessService.getResultQueueSize();
        long currentProcessed = processedResults.get();
        long lastProcessed = lastProcessedResults.getAndSet(currentProcessed);

        log.info("Result executor health: active={}, poolSize={}, queueSize={}, completed={}, hazelcastQueue={}, currentProcessed={}, lastProcessed={}",
                resultProcessingExecutor.getActiveCount(), resultProcessingExecutor.getPoolSize(), resultProcessingExecutor.getQueue().size(),
                resultProcessingExecutor.getCompletedTaskCount(), hazelcastQueueSize, currentProcessed, lastProcessed);

        if (hazelcastQueueSize > 0 && currentProcessed == lastProcessed) {
            // We had items in the queue, but processed nothing in the 5 minutes.
            log.error("Result processing seems stuck: hazelcastQueueSize={} and processedResults did not increase.", hazelcastQueueSize);
            log.error("Consider restarting the application if this issue persists.");
        }
    }

    /**
     * Removes the item listener from the Hazelcast result queue if the instance is active.
     * Logs an error if Hazelcast is not running.
     */
    @PreDestroy
    public void removeListener() {
        if (distributedDataAccessService.isInstanceRunning() && this.listenerId != null) {
            distributedDataAccessService.getDistributedBuildResultQueue().removeListener(this.listenerId);
        }
        shutdownResultProcessingExecutor();
    }

    private void shutdownResultProcessingExecutor() {
        if (resultProcessingExecutor == null || resultProcessingExecutor.isShutdown()) {
            return;
        }

        resultProcessingExecutor.shutdown();
        try {
            boolean terminated = resultProcessingExecutor.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                log.warn("Result processing executor did not terminate in time, forcing shutdown");
                resultProcessingExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Result processing executor termination interrupted", e);
            resultProcessingExecutor.shutdownNow();
        }
    }

    /**
     * Submit an asynchronous task that polls one item from the result queue and processes it.
     */
    public void processResultAsync() {
        try {
            resultProcessingExecutor.execute(this::processResult);
        }
        catch (RejectedExecutionException ex) {
            // this is not an issue as we rely on the queue and will continue polling from it once another
            // event listener or schedule triggers
            log.debug("Result processing executor queue is full.");
        }
    }

    /**
     * Polls a build job result from the build job queue, notifies the user about the result and saves the result to the database.
     */
    private void processResult() {
        ResultQueueItem resultQueueItem = distributedDataAccessService.getDistributedBuildResultQueue().poll();

        if (resultQueueItem == null) {
            return;
        }
        log.info("Processing build job result with id {}", resultQueueItem.buildJobQueueItem().id());
        log.debug("Build jobs waiting in queue: {}", distributedDataAccessService.getResultQueueSize());
        log.debug("Queued build jobs: {}", distributedDataAccessService.getResultQueueIds());

        BuildJobQueueItem buildJob = resultQueueItem.buildJobQueueItem();
        BuildResult buildResult = resultQueueItem.buildResult();
        List<BuildLogDTO> buildLogs = resultQueueItem.buildLogs();
        Throwable buildException = resultQueueItem.exception();

        if (buildResult == null) {
            return;
        }
        BuildJob savedBuildJob = null;
        Result result = null;
        // A container of a multi-container build persists its own build job (linked to the shared result) inside the
        // synchronized aggregation below, so the fallback save in the finally block is skipped for it.
        boolean buildJobPersisted = false;

        SecurityUtils.setAuthorizationObject();
        Optional<Participation> participationOptional = participationRepository.findWithProgrammingExerciseWithBuildConfigById(buildJob.participationId());

        try {
            if (participationOptional.isPresent()) {
                ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();

                // In case the participation does not contain the exercise, we have to load it from the database
                if (participation.getProgrammingExercise() == null) {
                    participation.setProgrammingExercise(programmingExerciseRepository.getProgrammingExerciseWithBuildConfigFromParticipation(participation));
                }

                boolean testsExpected = buildJob.buildConfig().areTestsExpected();
                if (buildJob.containerName() != null) {
                    // One container of a multi-container build: aggregate its feedback into the submission's shared result.
                    result = processContainerResult(participation, buildJob, buildResult, buildException, testsExpected);
                    buildJobPersisted = result != null;
                }
                else {
                    result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult, testsExpected);
                }
            }
            else {
                log.warn("Participation with id {} has been deleted. Cancelling the processing of the build result.", buildJob.participationId());
            }
        }
        finally {
            processedResults.incrementAndGet();
            ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participationOptional.orElse(null);
            if (programmingExerciseParticipation != null && programmingExerciseParticipation.getExercise() == null) {
                ProgrammingExercise exercise = programmingExerciseRepository.getProgrammingExerciseWithBuildConfigFromParticipation(programmingExerciseParticipation);
                programmingExerciseParticipation.setExercise(exercise);
                programmingExerciseParticipation.setProgrammingExercise(exercise);
            }

            // save build job to database (the multi-container path already persisted it linked to the aggregated result)
            if (buildJobPersisted) {
                savedBuildJob = buildJobRepository.findByBuildJobId(buildJob.id()).orElse(null);
            }
            else {
                BuildStatus buildStatus = determineBuildStatus(buildJob, buildException);
                if (buildStatus == BuildStatus.FAILED) {
                    log.error("Error while processing build job: {}", buildJob, buildException);
                }
                savedBuildJob = saveFinishedBuildJob(buildJob, buildStatus, result);
            }
            if (buildException == null && programmingExerciseParticipation != null) {
                updateExerciseBuildDurationAsync(programmingExerciseParticipation.getProgrammingExercise().getId());
            }

            if (programmingExerciseParticipation != null) {
                if (result != null) {
                    programmingMessagingService.notifyUserAboutNewResult(result, programmingExerciseParticipation);
                }
                else {
                    log.error("Result could not be processed for build job: {}", buildJob);
                    programmingSubmissionMessagingService.notifyUserAboutSubmissionError((Participation) programmingExerciseParticipation,
                            new BuildTriggerWebsocketError("Result could not be processed", programmingExerciseParticipation.getId()));
                }

                if (!buildLogs.isEmpty()) {
                    if (savedBuildJob != null) {
                        buildLogEntryService.saveBuildLogsToFile(buildLogs, savedBuildJob.getBuildJobId(), programmingExerciseParticipation.getProgrammingExercise());
                    }
                    else {
                        log.warn("Couldn't save build logs as build job {} was not saved", buildJob.id());
                    }
                }
            }
        }

        // If the build job is a solution build of a test or auxiliary push, we need to trigger the build of the corresponding template repository
        if (isSolutionBuildOfTestOrAuxPush(buildJob)) {
            log.info("Triggering build of template repository for solution build with id {}", buildJob.id());
            try {
                // Run async to not block the result processing thread
                CompletableFuture.runAsync(() -> {
                    SecurityUtils.setAuthorizationObject();
                    programmingTriggerService.triggerTemplateBuildAndNotifyUser(buildJob.exerciseId(), buildJob.buildConfig().testCommitHash(), SubmissionType.TEST,
                            buildJob.repositoryInfo().triggeredByPushTo());
                });
            }
            catch (EntityNotFoundException e) {
                // Something went wrong while retrieving the template participation.
                // At this point, programmingMessagingService.notifyUserAboutSubmissionError() does not work, because the template participation is not available.
                // The instructor will see in the UI that no build of the template repository was conducted and will receive an error message when triggering the build
                // manually.
                log.error("Something went wrong while triggering the template build for exercise {} after the solution build was finished.", buildJob.exerciseId(), e);
            }
        }
    }

    /**
     * Processes the result of one container of a multi-container build. The container's feedback is appended to the
     * submission's aggregated result, this container's build job is linked to that result, and the result is finalized
     * once every container has finished. The containers of one submission are serialized by a distributed lock on their
     * grouping key (participation and commit hash), so they aggregate into the same result and count completion without
     * racing, even when they are processed by several result-processing threads across several core nodes in parallel.
     *
     * @param participation  the participation that was built
     * @param buildJob       the finished build job of the container
     * @param buildResult    the build result of the container
     * @param buildException the exception that occurred during the build, if any
     * @param testsExpected  whether tests were expected for this container
     * @return the aggregated result (finalized once every container finished), or null if it could not be processed
     */
    private Result processContainerResult(ProgrammingExerciseParticipation participation, BuildJobQueueItem buildJob, BuildResult buildResult, Throwable buildException,
            boolean testsExpected) {
        // The containers of one submission are grouped by participation and commit hash, so a lock on that key serializes
        // them. It is a distributed lock (backed by the same cluster as the queues), so it also holds across the several
        // core nodes that process results in parallel, not only across the threads of one node. Without it, two nodes
        // could each create a separate aggregated result for the same submission and neither would ever reach the
        // expected container count.
        String lockKey = participation.getId() + "-" + buildResult.assignmentRepoCommitHash();
        DistributedMap<String, Boolean> aggregationLocks = distributedDataAccessService.getResultAggregationLockMap();
        aggregationLocks.lock(lockKey);
        try {
            // Append, link and finalize run in one programmatic transaction that commits before the lock is released, so
            // the next container of the same submission sees the appended feedback and the linked build job atomically.
            return transactionTemplate.execute(status -> {
                int expectedContainerCount = determineExpectedContainerCount(participation);
                Result aggregatedResult = programmingExerciseGradingService.appendContainerResult(participation, buildResult, testsExpected, expectedContainerCount,
                        buildJob.containerName());
                if (aggregatedResult == null) {
                    return null;
                }

                BuildStatus buildStatus = determineBuildStatus(buildJob, buildException);
                // Link this container's build job to the shared result so finished containers can be counted below.
                saveFinishedBuildJob(buildJob, buildStatus, aggregatedResult);

                long completedContainers = buildJobRepository.countByResultId(aggregatedResult.getId());
                if (completedContainers >= expectedContainerCount) {
                    boolean allContainersSucceeded = !buildJobRepository.existsByResultIdAndBuildStatusNot(aggregatedResult.getId(), BuildStatus.SUCCESSFUL);
                    return programmingExerciseGradingService.finalizeContainerResult(aggregatedResult, participation, allContainersSucceeded, buildResult.buildRunDate());
                }
                return aggregatedResult;
            });
        }
        finally {
            aggregationLocks.unlock(lockKey);
        }
    }

    /**
     * Determines how many containers are expected to contribute to the submission's result, from the current build plan
     * of the exercise. It matches the number of build jobs the trigger scheduled for the commit unless the build plan
     * was edited in between.
     *
     * @param participation the participation that was built
     * @return the expected number of containers, at least one
     */
    private int determineExpectedContainerCount(ProgrammingExerciseParticipation participation) {
        var buildConfig = participation.getProgrammingExercise().getBuildConfig();
        if (buildConfig == null) {
            return 1;
        }
        try {
            return Math.max(BuildPlanPhasesDTO.fromBuildPlanConfiguration(buildConfig.getBuildPlanConfiguration()).effectiveContainers().size(), 1);
        }
        catch (JsonProcessingException e) {
            log.warn("Could not determine the expected container count for participation {}, assuming a single container", participation.getId(), e);
            return 1;
        }
    }

    /**
     * Maps the outcome of a build job to its status, preserving the cancellation and timeout handling of the single
     * result path.
     *
     * @param buildJob       the finished build job
     * @param buildException the exception that occurred during the build, if any
     * @return the build status
     */
    private BuildStatus determineBuildStatus(BuildJobQueueItem buildJob, Throwable buildException) {
        if (buildException == null) {
            return BuildStatus.SUCCESSFUL;
        }
        if (buildException.getCause() instanceof CancellationException && buildException.getMessage().equals("Build job with id " + buildJob.id() + " was cancelled.")) {
            return BuildStatus.CANCELLED;
        }
        if (buildException.getCause() instanceof TimeoutException) {
            return BuildStatus.TIMEOUT;
        }
        return BuildStatus.FAILED;
    }

    /**
     * Save a finished build job to the database and send a WebSocket notification.
     *
     * @param queueItem   the build job object from the queue
     * @param buildStatus the status of the build job (SUCCESSFUL, FAILED, CANCELLED)
     * @param result      the submission result
     *
     * @return the saved the build job
     */
    private BuildJob saveFinishedBuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, Result result) {
        try {
            BuildJob buildJob = new BuildJob(queueItem, buildStatus, result);
            buildJobRepository.findByBuildJobId(queueItem.id()).ifPresent(existingBuildJob -> buildJob.setId(existingBuildJob.getId()));
            BuildJob savedBuildJob = buildJobRepository.save(buildJob);

            // Send WebSocket notification for the finished build job
            // Refetch with eager loading to avoid LazyInitializationException
            final BuildJob finalSavedBuildJob = savedBuildJob;
            localCIQueueWebsocketService.ifPresent(service -> {
                try {
                    buildJobRepository.findWithDataByBuildJobId(finalSavedBuildJob.getBuildJobId()).ifPresent(buildJobWithData -> {
                        FinishedBuildJobDTO finishedBuildJobDTO = FinishedBuildJobDTO.of(buildJobWithData);
                        service.sendFinishedBuildJobOverWebsocket(finishedBuildJobDTO);
                    });
                }
                catch (Exception e) {
                    log.warn("Could not send finished build job notification over WebSocket", e);
                }
            });

            return savedBuildJob;
        }
        catch (Exception e) {
            log.error("Could not save build job to database", e);
            return null;
        }
    }

    private void updateExerciseBuildDurationAsync(long exerciseId) {
        CompletableFuture.runAsync(() -> updateExerciseBuildDuration(exerciseId));
    }

    private void updateExerciseBuildDuration(long exerciseId) {
        try {
            var buildStatisticsDto = buildJobRepository.findBuildJobStatisticsByExerciseId(exerciseId);
            if (buildStatisticsDto == null || buildStatisticsDto.buildCountWhenUpdated() == 0) {
                return;
            }

            long averageDuration = Math.round(buildStatisticsDto.buildDurationSeconds());

            var programmingExerciseBuildStatisticsOpt = programmingExerciseBuildStatisticsRepository.findByExerciseId(exerciseId);

            if (programmingExerciseBuildStatisticsOpt.isEmpty()) {
                // create the database row if it does not exist
                var programmingExerciseBuildStatistics = new ProgrammingExerciseBuildStatistics(exerciseId, averageDuration, buildStatisticsDto.buildCountWhenUpdated());
                programmingExerciseBuildStatisticsRepository.save(programmingExerciseBuildStatistics);
            }
            else {
                var programmingExerciseBuildStatistics = programmingExerciseBuildStatisticsOpt.get();
                // only update the database row if the build duration has changed using a modifying query or when the build count is above a certain threshold
                if (averageDuration == programmingExerciseBuildStatistics.getBuildDurationSeconds()
                        && buildStatisticsDto.buildCountWhenUpdated() - programmingExerciseBuildStatistics.getBuildCountWhenUpdated() < BUILD_STATISTICS_UPDATE_THRESHOLD) {
                    return;
                }
                programmingExerciseBuildStatisticsRepository.updateStatistics(averageDuration, buildStatisticsDto.buildCountWhenUpdated(), exerciseId);
            }

        }
        catch (Exception e) {
            log.error("Could not update exercise build duration", e);
        }
    }

    /**
     * Listener that reacts to new build results added to the distributed result queue.
     *
     * <p>
     * <strong>Responsibilities</strong>:
     * </p>
     * <ul>
     * <li>Trigger asynchronous post-processing of build results when a new {@link ResultQueueItem} arrives.</li>
     * <li>Keep the Hazelcast event thread lightweight by delegating all work to {@link #processResultAsync()}.</li>
     * <li>Log concise, context-rich messages for observability while avoiding excessive output.</li>
     * </ul>
     *
     * <p>
     * <strong>Notes</strong>:
     * </p>
     * <ul>
     * <li>Never perform blocking or long-running operations in the event callback.</li>
     * <li>All exceptions are caught and logged defensively to prevent listener crashes.</li>
     * </ul>
     */
    public class ResultQueueListener implements QueueItemListener<ResultQueueItem> {

        @Override
        public void itemAdded(ResultQueueItem item) {
            try {
                log.info("Result of build job with id {} added to queue. Will process one result async now", item.buildJobQueueItem().id());
                processResultAsync();
            }
            catch (Exception e) {
                log.error("Error handling itemAdded event in ResultQueueListener", e);
            }
        }

        @Override
        public void itemRemoved(ResultQueueItem item) {
            log.debug("Result removed from queue");
        }
    }

    /**
     * Checks if the given build job is a solution build of a test or auxiliary push.
     *
     * @param buildJob the build job to check
     * @return true if the build job is a solution build of a test or auxiliary push, false otherwise
     */
    private boolean isSolutionBuildOfTestOrAuxPush(BuildJobQueueItem buildJob) {
        return buildJob.repositoryInfo().repositoryType() == RepositoryType.SOLUTION
                && (buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.TESTS || buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.AUXILIARY);
    }
}
