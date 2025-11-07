package de.tum.cit.aet.artemis.programming.service.localci;

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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
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
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
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

    private UUID listenerId;

    @Value("${artemis.continuous-integration.concurrent-result-processing-size:16}")
    private int concurrentResultProcessingSize;

    private ThreadPoolExecutor resultProcessingExecutor;

    public LocalCIResultProcessingService(ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingMessagingService programmingMessagingService,
            BuildJobRepository buildJobRepository, ProgrammingExerciseRepository programmingExerciseRepository, ParticipationRepository participationRepository,
            ProgrammingTriggerService programmingTriggerService, BuildLogEntryService buildLogEntryService,
            ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository, DistributedDataAccessService distributedDataAccessService,
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService) {
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

        // buffer up to 1000 tasks before rejecting new tasks. Rejections will not lead to loss because the results maintain in the queue but this speeds up
        // result processing under high load so we do not need to wait for the polling schedule if many results are processed very fast.
        resultProcessingExecutor = new ThreadPoolExecutor(concurrentResultProcessingSize, concurrentResultProcessingSize, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1000), threadFactory, new ThreadPoolExecutor.AbortPolicy());
        log.info("Initialized LocalCI result processing executor with pool size {}", concurrentResultProcessingSize);
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
                result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);

            }
            else {
                log.warn("Participation with id {} has been deleted. Cancelling the processing of the build result.", buildJob.participationId());
            }
        }
        finally {
            ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participationOptional.orElse(null);
            if (programmingExerciseParticipation != null && programmingExerciseParticipation.getExercise() == null) {
                ProgrammingExercise exercise = programmingExerciseRepository.getProgrammingExerciseWithBuildConfigFromParticipation(programmingExerciseParticipation);
                programmingExerciseParticipation.setExercise(exercise);
                programmingExerciseParticipation.setProgrammingExercise(exercise);
            }

            // save build job to database
            if (buildException != null) {
                if (buildException.getCause() instanceof CancellationException && buildException.getMessage().equals("Build job with id " + buildJob.id() + " was cancelled.")) {
                    savedBuildJob = saveFinishedBuildJob(buildJob, BuildStatus.CANCELLED, result);
                }
                else if (buildException.getCause() instanceof TimeoutException) {
                    savedBuildJob = saveFinishedBuildJob(buildJob, BuildStatus.TIMEOUT, result);
                }
                else {
                    log.error("Error while processing build job: {}", buildJob, buildException);
                    savedBuildJob = saveFinishedBuildJob(buildJob, BuildStatus.FAILED, result);
                }
            }
            else {
                savedBuildJob = saveFinishedBuildJob(buildJob, BuildStatus.SUCCESSFUL, result);
                if (programmingExerciseParticipation != null) {
                    updateExerciseBuildDurationAsync(programmingExerciseParticipation.getProgrammingExercise().getId());
                }
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
     * Save a finished build job to the database.
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
