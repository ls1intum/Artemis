package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.ArrayList;
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

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationResultService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;

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

    private final ResultRepository resultRepository;

    private final FeedbackRepository feedbackRepository;

    private final TransactionTemplate transactionTemplate;

    private final Optional<ContinuousIntegrationResultService> continuousIntegrationResultService;

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
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService, ResultRepository resultRepository, FeedbackRepository feedbackRepository,
            TransactionTemplate transactionTemplate, Optional<ContinuousIntegrationResultService> continuousIntegrationResultService) {
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
        this.resultRepository = resultRepository;
        this.feedbackRepository = feedbackRepository;
        this.transactionTemplate = transactionTemplate;
        this.continuousIntegrationResultService = continuousIntegrationResultService;
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
        BuildJob savedBuildJob;
        Result result = null;

        SecurityUtils.setAuthorizationObject();
        Optional<Participation> participationOptional = participationRepository.findWithProgrammingExerciseWithBuildConfigById(buildJob.participationId());

        try {
            if (participationOptional.isPresent()) {
                ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();

                // In case the participation does not contain the exercise, we have to load it from the database
                if (participation.getProgrammingExercise() == null) {
                    participation.setProgrammingExercise(programmingExerciseRepository.getProgrammingExerciseWithBuildConfigFromParticipation(participation));
                }

                // For multi-container builds, aggregate feedback from all containers into a single Result
                // This also saves the BuildJob to prevent race conditions
                result = getOrCreateResultForSubmission(participation, buildJob, buildResult, buildException);

            }
            else {
                log.warn("Participation with id {} has been deleted. Cancelling the processing of the build result.", buildJob.participationId());
                // Still save the build job even if participation is missing
                BuildStatus status = determineBuildStatus(buildResult, buildException);
                addResultToBuildJob(buildJob, status, null);
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

            // Build job is already saved in getOrCreateResultForSubmission or in the else block above
            // Just update build duration statistics for successful builds (only if build actually succeeded)
            if (buildException == null && buildResult != null && buildResult.isBuildSuccessful() && programmingExerciseParticipation != null) {
                updateExerciseBuildDurationAsync(programmingExerciseParticipation.getProgrammingExercise().getId());
            }

            // Retrieve the saved build job for logging and error handling
            savedBuildJob = buildJobRepository.findByBuildJobId(buildJob.id()).orElse(null);

            if (programmingExerciseParticipation != null) {
                // For multi-container builds, feedback from all containers is aggregated into a single Result
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
     * Gets the existing Result for this submission or creates a new one.
     * For multi-container builds, this ensures all containers append their feedback to the same Result.
     * Also saves the BuildJob-Result association within a transaction and synchronized block to prevent race conditions.
     *
     * @param participation  the participation
     * @param buildJob       the build job queue item
     * @param buildResult    the build result containing feedback from this container
     * @param buildException the exception that occurred during the build, if any
     * @return the Result object (new or existing with appended feedback)
     */
    private synchronized Result getOrCreateResultForSubmission(ProgrammingExerciseParticipation participation, BuildJobQueueItem buildJob, BuildResult buildResult,
            Throwable buildException) {
        // Use programmatic transaction management to ensure the transaction commits within the synchronized block
        return transactionTemplate.execute(status -> {
            return getOrCreateResultForSubmissionInternal(participation, buildJob, buildResult, buildException);
        });
    }

    /**
     * Internal method that performs the actual work of getting or creating a Result.
     * Called within a transaction from getOrCreateResultForSubmission.
     */
    private Result getOrCreateResultForSubmissionInternal(ProgrammingExerciseParticipation participation, BuildJobQueueItem buildJob, BuildResult buildResult,
            Throwable buildException) {
        BuildStatus buildStatus = determineBuildStatus(buildResult, buildException);

        // Try to find an existing Result for this submission (from another container)
        Long submissionId = buildJob.submissionId();
        Result existingResult = findExistingResultForSubmission(submissionId);

        if (existingResult != null) {
            log.info("Found existing result {} for submission {}. Appending feedback from container {}", existingResult.getId(), submissionId, buildJob.containerId());

            // Extract feedbacks from this container's buildResult by creating a temporary result
            ContinuousIntegrationResultService ciResultService = continuousIntegrationResultService.orElseThrow();
            Result tempResult = ciResultService.createResultFromBuildResult(buildResult, participation);

            if (tempResult.getFeedbacks() != null && !tempResult.getFeedbacks().isEmpty()) {
                saveFeedbacksForResult(existingResult, tempResult.getFeedbacks());
            }

            // Handle build logs for this container (only if build produced logs and wasn't successful)
            if (buildResult.hasLogs() && !buildResult.isBuildSuccessful()) {
                String containerName = getContainerName(participation.getProgrammingExercise(), buildJob.containerId());
                saveBuildLogsForContainer(buildResult, existingResult, containerName, participation.getProgrammingExercise());
            }

            // Immediately save this BuildJob with the shared Result to prevent race conditions
            addResultToBuildJob(buildJob, buildStatus, existingResult);

            finalizeAggregatedResultIfComplete(existingResult, participation, buildResult);

            return existingResult;
        }

        // No existing result found, create a new one
        log.info("Creating new result for build job {} and container {}", buildJob.id(), buildJob.containerId());
        Result newResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);

        // Update container_name on any build logs that were saved for this result
        if (newResult != null && newResult.getSubmission() instanceof ProgrammingSubmission programmingSubmission) {
            String containerName = getContainerName(participation.getProgrammingExercise(), buildJob.containerId());
            updateContainerNameOnBuildLogs(programmingSubmission, containerName);
        }

        // Immediately save this BuildJob with the new Result to prevent race conditions
        addResultToBuildJob(buildJob, buildStatus, newResult);

        return newResult;
    }

    /**
     * Gets the container name from the container ID by looking it up in the exercise's build config.
     *
     * @param exercise    the programming exercise
     * @param containerId the container ID
     * @return the container name, or a default name if not found
     */
    private String getContainerName(ProgrammingExercise exercise, Long containerId) {
        if (containerId == null || exercise == null || exercise.getBuildConfig() == null) {
            return "Container";
        }

        var buildConfig = exercise.getBuildConfig();
        var containerConfig = buildConfig.getContainerConfigs().values().stream().filter(c -> containerId.equals(c.getId())).findFirst().orElse(null);

        if (containerConfig != null && containerConfig.getName() != null) {
            return containerConfig.getName();
        }

        // Fallback to "Container {id}" if name not found
        return "Container " + containerId;
    }

    /**
     * Updates the container_name on build log entries for a submission.
     *
     * @param submission    the programming submission
     * @param containerName the container name
     */
    private void updateContainerNameOnBuildLogs(ProgrammingSubmission submission, String containerName) {
        List<BuildLogEntry> buildLogs = buildLogEntryService.getLatestBuildLogs(submission);
        for (BuildLogEntry logEntry : buildLogs) {
            if (logEntry.getContainerName() == null) {
                logEntry.setContainerName(containerName);
            }
        }
        buildLogEntryService.saveBuildLogs(buildLogs, submission);
    }

    /**
     * Saves build logs for a specific container when appending to an existing result.
     *
     * @param buildResult   the build result containing logs
     * @param result        the result to associate logs with
     * @param containerName the container name
     * @param exercise      the programming exercise
     */
    private void saveBuildLogsForContainer(BuildResult buildResult, Result result, String containerName, ProgrammingExercise exercise) {
        if (result.getSubmission() instanceof ProgrammingSubmission programmingSubmission) {
            var buildLogs = buildResult.extractBuildLogs();
            var programmingLanguage = exercise.getProgrammingLanguage();

            // Remove unnecessary logs and set container name
            buildLogs = buildLogEntryService.removeUnnecessaryLogsForProgrammingLanguage(buildLogs, programmingLanguage);

            // Set container name on all log entries
            for (BuildLogEntry logEntry : buildLogs) {
                logEntry.setContainerName(containerName);
            }

            var savedBuildLogs = buildLogEntryService.saveBuildLogs(buildLogs, programmingSubmission);

            // Mark submission as build failed if not already marked
            if (!programmingSubmission.isBuildFailed()) {
                programmingSubmission.setBuildFailed(true);
            }

            // Append to existing logs instead of replacing them
            List<BuildLogEntry> existingLogs = new ArrayList<>(programmingSubmission.getBuildLogEntries());
            existingLogs.addAll(savedBuildLogs);
            programmingSubmission.setBuildLogEntries(existingLogs);
        }
    }

    private void saveFeedbacksForResult(Result result, List<Feedback> feedbacks) {
        feedbacks.forEach(feedback -> feedback.setResult(result));
        feedbackRepository.saveAll(feedbacks);
        result.addFeedbacks(feedbacks);
    }

    private void finalizeAggregatedResultIfComplete(Result result, ProgrammingExerciseParticipation participation, BuildResult buildResult) {
        if (!(result.getSubmission() instanceof ProgrammingSubmission submission)) {
            return;
        }

        Integer expectedContainerCount = submission.getExpectedContainerCount();
        if (expectedContainerCount == null || expectedContainerCount <= 1) {
            return;
        }
        if (result.getCompletionDate() != null) {
            return;
        }

        long completedContainers = buildJobRepository.countByResultId(result.getId());
        if (completedContainers < expectedContainerCount) {
            return;
        }

        Result aggregatedResult = resultRepository.findByIdWithEagerFeedbacksElseThrow(result.getId());
        boolean isStudentParticipation = participation instanceof ProgrammingExerciseStudentParticipation;
        programmingExerciseGradingService.calculateScoreForResult(aggregatedResult, participation.getProgrammingExercise(), isStudentParticipation);

        boolean anyFailed = buildJobRepository.existsByResultIdAndBuildStatusNot(result.getId(), BuildStatus.SUCCESSFUL);
        aggregatedResult.setSuccessful(!anyFailed);
        aggregatedResult.setCompletionDate(buildResult.buildRunDate());

        resultRepository.save(aggregatedResult);
    }

    /**
     * Determines the build status based on the build result and exception.
     *
     * @param buildResult    the build result
     * @param buildException the exception that occurred during the build
     * @return the build status
     */
    private BuildStatus determineBuildStatus(BuildResult buildResult, Throwable buildException) {
        if (buildException != null) {
            if (buildException.getCause() instanceof CancellationException && buildException.getMessage().contains("was cancelled")) {
                return BuildStatus.CANCELLED;
            }
            else if (buildException.getCause() instanceof TimeoutException) {
                return BuildStatus.TIMEOUT;
            }
            else {
                return BuildStatus.FAILED;
            }
        }
        // Check if the build itself was successful (Gradle/Maven succeeded)
        if (buildResult != null && !buildResult.isBuildSuccessful()) {
            return BuildStatus.FAILED;
        }
        return BuildStatus.SUCCESSFUL;
    }

    /**
     * Save a finished build job to the database.
     *
     * @param queueItem   the build job object from the queue
     * @param buildStatus the status of the build job (SUCCESSFUL, FAILED, CANCELLED)
     * @param result      the submission result
     *
     * @return the saved build job
     */
    private BuildJob addResultToBuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, Result result) {
        try {
            var found = buildJobRepository.findByBuildJobIdAndResult(queueItem.id());
            BuildJob buildJob;
            if (found.isPresent()) {
                buildJob = found.get();
                buildJob.setResult(result);
                buildJob.setBuildStatus(buildStatus);
            }
            else {
                buildJob = new BuildJob(queueItem, buildStatus, result);
            }

            return buildJobRepository.save(buildJob);
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
     * Finds an existing Result for the given submission ID.
     * This is used in multi-container builds to find the Result that another container has already created.
     *
     * @param submissionId the submission ID
     * @return the existing Result with eager-loaded feedbacks, or null if not found
     */
    private Result findExistingResultForSubmission(Long submissionId) {
        if (submissionId == null) {
            return null;
        }

        log.debug("Searching for existing result for submission {}", submissionId);

        // Find the latest result for this submission
        Optional<Result> resultOpt = resultRepository.findLatestResultWithFeedbacksAndTestcasesForSubmission(submissionId);

        if (resultOpt.isPresent()) {
            log.debug("Found existing result {} for submission {}", resultOpt.get().getId(), submissionId);
            return resultOpt.get();
        }

        log.debug("No existing result found for submission {}", submissionId);
        return null;
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
