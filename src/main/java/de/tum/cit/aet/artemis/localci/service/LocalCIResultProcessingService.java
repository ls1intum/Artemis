package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResultLimits;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
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

    private final LocalCIBuildGroupService buildGroupService;

    private final LocalCIFinishedBuildJobService finishedBuildJobService;

    private final ProgrammingExerciseDeletionService programmingExerciseDeletionService;

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
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService, ProgrammingExerciseDeletionService programmingExerciseDeletionService,
            LocalCIBuildGroupService buildGroupService, LocalCIFinishedBuildJobService finishedBuildJobService) {
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
        this.programmingExerciseDeletionService = programmingExerciseDeletionService;
        this.buildGroupService = buildGroupService;
        this.finishedBuildJobService = finishedBuildJobService;
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
        submitResultProcessing(distributedDataAccessService.getDistributedBuildResultQueue());
    }

    /** Retry one deferred result without creating a queue-listener feedback loop. */
    public void processDeferredResultAsync() {
        submitResultProcessing(distributedDataAccessService.getDistributedDeferredBuildResultQueue());
    }

    private void submitResultProcessing(DistributedQueue<ResultQueueItem> queue) {
        try {
            resultProcessingExecutor.execute(() -> processResult(queue));
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
    private void processResult(DistributedQueue<ResultQueueItem> queue) {
        ResultQueueItem resultQueueItem = queue.poll();

        if (resultQueueItem == null) {
            return;
        }
        var admission = programmingExerciseDeletionService.tryAdmitResultProcessing(resultQueueItem.buildJobQueueItem().exerciseId());
        if (admission.isEmpty()) {
            // Preserve denied results without occupying a worker or firing more queue-listener events.
            distributedDataAccessService.getDistributedDeferredBuildResultQueue().add(resultQueueItem);
            return;
        }
        try (var permit = admission.get()) {
            log.info("Processing build job result with id {}", resultQueueItem.buildJobQueueItem().id());
            if (log.isDebugEnabled()) {
                log.debug("Build jobs waiting in queue: {}", distributedDataAccessService.getResultQueueSize());
                log.debug("Queued build jobs: {}", distributedDataAccessService.getResultQueueIds());
            }
            processResultLocked(resultQueueItem);
        }
    }

    /**
     * Checks that the ids that a queued build job declares belong together in the database: the exercise of the participation is the exercise of the job, the course of that
     * exercise is the course of the job, and an existing record of the job id belongs to the same participation, exercise and course.
     * Existing job ownership is checked even when the participation has been deleted.
     * <p>
     * The processing list of the build agents is deliberately not consulted: an agent removes a job from it before it delivers the result.
     */
    private boolean isConsistentWithDatabase(BuildJobQueueItem buildJob, Optional<Participation> participation) {
        if (participation.isPresent()) {
            if (!(participation.get() instanceof ProgrammingExerciseParticipation programmingParticipation)) {
                return false;
            }
            ProgrammingExercise exercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(programmingParticipation);
            if (exercise == null || !Objects.equals(exercise.getId(), buildJob.exerciseId())) {
                return false;
            }
            Optional<Long> courseId = programmingExerciseRepository.findCourseIdById(buildJob.exerciseId());
            if (courseId.isEmpty() || !Objects.equals(courseId.get(), buildJob.courseId())) {
                return false;
            }
        }
        return buildJobRepository.findByBuildJobId(buildJob.id())
                .map(existing -> (existing.getParticipationId() == null || Objects.equals(existing.getParticipationId(), buildJob.participationId()))
                        && (existing.getExerciseId() == null || Objects.equals(existing.getExerciseId(), buildJob.exerciseId()))
                        && (existing.getCourseId() == null || Objects.equals(existing.getCourseId(), buildJob.courseId())))
                .orElse(true);
    }

    private void processResultLocked(ResultQueueItem resultQueueItem) {
        BuildJobQueueItem buildJob = resultQueueItem.buildJobQueueItem();
        BuildResult buildResult = resultQueueItem.buildResult();
        List<BuildLogDTO> buildLogs = resultQueueItem.buildLogs();
        Throwable buildException = resultQueueItem.exception();

        if (buildResult == null) {
            return;
        }
        // The result was created on a build agent, so the number of its elements is bounded before it is processed
        BuildResult limitedBuildResult = BuildResultLimits.limit(buildResult);
        List<BuildLogDTO> limitedBuildLogs = BuildResultLimits.limitLogs(buildLogs);
        if (limitedBuildResult != buildResult || limitedBuildLogs != buildLogs) {
            log.warn("The result of build job {} exceeds the limits for the number of tests, reports or log entries and is shortened", buildJob.id());
            buildResult = limitedBuildResult;
            buildLogs = limitedBuildLogs;
        }
        Result result = null;
        // Whether the result is the one to report: a single-container result always is, a container's once it completed its group.
        boolean completedResult = buildJob.buildGroup() == null;
        boolean resultExpected = true;
        BuildJob savedBuildJob = null;
        BuildStatus buildStatus = determineBuildStatus(buildJob, buildException);

        SecurityUtils.setSystemAuthorizationObject();
        Optional<Participation> participationOptional;
        try {
            participationOptional = participationRepository.findWithProgrammingExerciseById(buildJob.participationId());
            if (!isConsistentWithDatabase(buildJob, participationOptional)) {
                // The ids of the item come from the queue, so a result is processed only for the participation, exercise and course that the database holds together.
                // A job whose result is dropped here is recorded as missing and is retried by the LocalCIMissingJobService.
                log.error("Dropping the result of build job {}: its participation {}, exercise {} and course {} do not belong together", buildJob.id(), buildJob.participationId(),
                        buildJob.exerciseId(), buildJob.courseId());
                return;
            }
        }
        catch (DataAccessException e) {
            // The result was already polled from the queue, so it is kept for another attempt instead of being lost. A retry that finds the database still unavailable keeps it
            // again.
            log.warn("Could not check the result of build job {} against the database, it is processed later: {}", buildJob.id(), e.getMessage());
            distributedDataAccessService.getDistributedDeferredBuildResultQueue().add(resultQueueItem);
            return;
        }

        try {
            if (participationOptional.isPresent()) {
                ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();

                // In case the participation does not contain the exercise, we have to load it from the database
                if (participation.getProgrammingExercise() == null) {
                    participation.setProgrammingExercise(programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation));
                }

                boolean testsExpected = buildJob.buildConfig().areTestsExpected();
                if (buildJob.buildGroup() != null) {
                    LocalCIBuildGroupService.ContainerOutcome outcome = buildGroupService.processContainerResult(participation, buildJob, buildResult, buildLogs, buildStatus,
                            testsExpected);
                    if (outcome != null) {
                        result = outcome.result();
                        savedBuildJob = outcome.savedBuildJob();
                        completedResult = outcome.completed();
                        resultExpected = outcome.resultExpected();
                    }
                }
                else {
                    result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult, testsExpected);
                }
            }
            else {
                log.warn("Participation with id {} has been deleted. Cancelling the processing of the build result.", buildJob.participationId());
            }
        }
        catch (DataIntegrityViolationException exception) {
            if (participationRepository.existsById(buildJob.participationId())) {
                throw exception;
            }
            // The participation was deleted after it was loaded above, so the result or its submission can no longer be written. There is nothing left to report to.
            log.warn("Participation with id {} was deleted while its build result was processed. Dropping the build result.", buildJob.participationId(), exception);
            participationOptional = Optional.empty();
        }
        finally {
            processedResults.incrementAndGet();
            ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participationOptional.orElse(null);
            if (programmingExerciseParticipation != null && programmingExerciseParticipation.getExercise() == null) {
                ProgrammingExercise exercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(programmingExerciseParticipation);
                programmingExerciseParticipation.setExercise(exercise);
                programmingExerciseParticipation.setProgrammingExercise(exercise);
            }

            if (buildStatus == BuildStatus.FAILED) {
                log.error("Error while processing build job: {}", buildJob, buildException);
            }
            // Save the build job to the database, unless the container path already saved it linked to the aggregated result.
            if (savedBuildJob == null) {
                savedBuildJob = finishedBuildJobService.saveFinishedBuildJob(buildJob, buildStatus, result);
            }
            if (buildException == null && programmingExerciseParticipation != null) {
                updateExerciseBuildDurationAsync(programmingExerciseParticipation.getProgrammingExercise().getId());
            }

            if (programmingExerciseParticipation != null) {
                if (result == null && resultExpected) {
                    log.error("Result could not be processed for build job: {}", buildJob);
                    programmingSubmissionMessagingService.notifyUserAboutSubmissionError((Participation) programmingExerciseParticipation,
                            new BuildTriggerWebsocketError("Result could not be processed", programmingExerciseParticipation.getId()));
                }
                // A container's partial result must not be reported: the client clears the pending submission on any result, and
                // the same call sends the score over LTI and to Iris. Completion comes from the outcome, since a draft assessment
                // the feedback was merged into has no completion date.
                else if (completedResult) {
                    programmingMessagingService.notifyUserAboutNewResult(result, programmingExerciseParticipation);
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

        // If the build job is a solution build of a test or auxiliary push, we need to trigger the build of the corresponding template repository.
        // A multi-container build triggers it once, from the container that completed its group.
        if (isSolutionBuildOfTestOrAuxPush(buildJob) && completedResult) {
            triggerTemplateBuild(buildJob.exerciseId(), buildJob.id(), buildJob.buildConfig().testCommitHash(), buildJob.repositoryInfo().triggeredByPushTo());
        }
    }

    private void triggerTemplateBuild(long exerciseId, String buildJobId, String testCommitHash, RepositoryType triggeredByPushTo) {
        log.info("Triggering build of template repository for solution build with id {}", buildJobId);
        try {
            // Keep lifecycle-lock waits off both result workers and the shared ForkJoinPool.
            // The independent thread establishes its own security context.
            runFollowUpAsync(() -> SecurityUtils.runAsSystem(() -> runWhileExerciseExists(exerciseId,
                    () -> programmingTriggerService.triggerTemplateBuildAndNotifyUser(exerciseId, testCommitHash, SubmissionType.TEST, triggeredByPushTo))));
        }
        catch (EntityNotFoundException e) {
            // Something went wrong while retrieving the template participation.
            // At this point, programmingMessagingService.notifyUserAboutSubmissionError() does not work, because the template participation is not available.
            // The instructor will see in the UI that no build of the template repository was conducted and will receive an error message when triggering the build
            // manually.
            log.error("Something went wrong while triggering the template build for exercise {} after the solution build was finished.", exerciseId, e);
        }
    }

    /**
     * Closes the build groups whose aggregated result stayed in progress although no container will complete it, see
     * {@link LocalCIBuildGroupService}, and reports each finalized result like a completed build: to the student, and for a
     * solution build of a test or auxiliary push by building the template.
     *
     * @param maxMissingJobRetries    the number of retries after which a missing job is not retried again
     * @param missingJobsRetriedSince the start of the retry window: a missing job submitted before it is not retried
     * @return the number of build groups finalized by this run
     */
    public int finalizeCompletedBuildGroups(int maxMissingJobRetries, ZonedDateTime missingJobsRetriedSince) {
        return buildGroupService.finalizeCompletedBuildGroups(maxMissingJobRetries, missingJobsRetriedSince, this::reportFinalizedBuildGroup);
    }

    private void reportFinalizedBuildGroup(Result finalizedResult, ProgrammingExerciseParticipation participation, BuildJob buildJob) {
        programmingMessagingService.notifyUserAboutNewResult(finalizedResult, participation);
        if (buildJob.getRepositoryType() == RepositoryType.SOLUTION
                && (buildJob.getTriggeredByPushTo() == RepositoryType.TESTS || buildJob.getTriggeredByPushTo() == RepositoryType.AUXILIARY)) {
            triggerTemplateBuild(buildJob.getExerciseId(), buildJob.getBuildJobId(), testCommitHashOf(finalizedResult, buildJob), buildJob.getTriggeredByPushTo());
        }
    }

    /**
     * The test commit a solution build was built against, read from its result because the build config that names it is
     * gone once the job has left the queue.
     */
    private static String testCommitHashOf(Result finalizedResult, BuildJob buildJob) {
        if (finalizedResult.getSubmission() instanceof ProgrammingSubmission submission && submission.getType() == SubmissionType.TEST && submission.getCommitHash() != null) {
            return submission.getCommitHash();
        }
        return buildJob.getCommitHash();
    }

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

    private void updateExerciseBuildDurationAsync(long exerciseId) {
        runFollowUpAsync(() -> runWhileExerciseExists(exerciseId, () -> updateExerciseBuildDuration(exerciseId)));
    }

    private void runFollowUpAsync(Runnable operation) {
        Thread.ofVirtual().name("local-ci-result-follow-up").start(() -> {
            try {
                operation.run();
            }
            catch (Exception e) {
                log.error("Asynchronous result follow-up failed", e);
            }
        });
    }

    private void runWhileExerciseExists(long exerciseId, Runnable operation) {
        try (var permit = programmingExerciseDeletionService.awaitResultProcessing(exerciseId)) {
            if (programmingExerciseRepository.existsById(exerciseId)) {
                operation.run();
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
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
