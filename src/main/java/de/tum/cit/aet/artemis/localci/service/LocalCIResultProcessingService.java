package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService.AppendedContainerResult;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;

@Profile(PROFILE_LOCALCI)
@Lazy
@Service
public class LocalCIResultProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIResultProcessingService.class);

    static final Set<BuildStatus> FINISHED_BUILD_STATUSES = Set.of(BuildStatus.SUCCESSFUL, BuildStatus.FAILED, BuildStatus.ERROR, BuildStatus.CANCELLED, BuildStatus.TIMEOUT);

    private static final int BUILD_STATISTICS_UPDATE_THRESHOLD = 10;

    /** How long the sweep leaves a group whose last job just finished to that container's own finalization. */
    private static final Duration COMPLETED_GROUP_GRACE_PERIOD = Duration.ofMinutes(2);

    private static final int COMPLETED_GROUPS_PER_SWEEP = 50;

    /** How far back the sweep looks: a group is closed within minutes, or within the missing-job retry window once it lost a container. */
    private static final Duration SWEEP_LOOKBACK = Duration.ofDays(1);

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
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService, Optional<LocalCIQueueWebsocketService> localCIQueueWebsocketService) {
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
        Result result = null;
        // Whether the result is the one to report: a single-container result always is, a container's once it completed its group.
        boolean completedResult = buildJob.buildGroup() == null;
        boolean resultExpected = true;
        BuildJob savedBuildJob = null;
        BuildStatus buildStatus = determineBuildStatus(buildJob, buildException);

        SecurityUtils.setSystemAuthorizationObject();
        Optional<Participation> participationOptional = participationRepository.findWithProgrammingExerciseById(buildJob.participationId());

        try {
            if (participationOptional.isPresent()) {
                ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();

                // In case the participation does not contain the exercise, we have to load it from the database
                if (participation.getProgrammingExercise() == null) {
                    participation.setProgrammingExercise(programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation));
                }

                boolean testsExpected = buildJob.buildConfig().areTestsExpected();
                if (buildJob.buildGroup() != null) {
                    ContainerOutcome outcome = processContainerResult(participation, buildJob, buildResult, buildLogs, buildStatus, testsExpected);
                    if (outcome != null) {
                        result = outcome.result();
                        savedBuildJob = outcome.savedBuildJob();
                        completedResult = outcome.completed();
                        resultExpected = !outcome.arrivedTooLate();
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
                savedBuildJob = saveFinishedBuildJob(buildJob, buildStatus, result);
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
            // Run async to not block the result processing thread
            // runAsync uses the common ForkJoinPool, which the Artemis async executors do not wrap, so this
            // lambda establishes its own context.
            CompletableFuture.runAsync(() -> SecurityUtils
                    .runAsSystem(() -> programmingTriggerService.triggerTemplateBuildAndNotifyUser(exerciseId, testCommitHash, SubmissionType.TEST, triggeredByPushTo)));
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
     * Closes the build groups whose aggregated result stayed in progress although no container will complete it. Append,
     * link and finalize commit separately, so a crash or a twice-failed finalization can leave a complete group open that
     * the missing-job retry never fires for. A group that lost a container stays open as well, since a retry builds again
     * as a new group. Its result is deleted if a later build of the same commit replaces the group, so the student does
     * not see a partial outcome, and finalized otherwise.
     *
     * @param maxMissingJobRetries    the number of retries after which a missing job is not retried again
     * @param missingJobsRetriedSince the start of the retry window: a missing job submitted before it is not retried
     * @return the number of build groups finalized by this run
     */
    public int finalizeCompletedBuildGroups(int maxMissingJobRetries, ZonedDateTime missingJobsRetriedSince) {
        ZonedDateTime now = ZonedDateTime.now();
        List<String> buildGroupIds = buildJobRepository.findCompletedBuildGroupsWithResultInProgress(FINISHED_BUILD_STATUSES, now.minus(SWEEP_LOOKBACK),
                now.minus(COMPLETED_GROUP_GRACE_PERIOD), maxMissingJobRetries, missingJobsRetriedSince, PageRequest.of(0, COMPLETED_GROUPS_PER_SWEEP));
        int finalizedGroups = 0;
        for (String buildGroupId : buildGroupIds) {
            try {
                if (finalizeCompletedBuildGroup(buildGroupId)) {
                    finalizedGroups++;
                }
            }
            catch (RuntimeException e) {
                log.error("Could not finalize the complete build group {} whose aggregated result stayed in progress", buildGroupId, e);
            }
        }
        return finalizedGroups;
    }

    private boolean finalizeCompletedBuildGroup(String buildGroupId) {
        DistributedMap<String, Boolean> aggregationLocks = distributedDataAccessService.getResultAggregationLockMap();
        aggregationLocks.lock(buildGroupId);
        try {
            // Re-checked under the lock: the last container may have finalized the group after the sweep's query ran.
            if (!buildJobRepository.existsResultInProgressOfBuildGroup(buildGroupId)) {
                return false;
            }
            List<BuildJob> jobs = buildJobRepository.findAllByBuildGroupId(buildGroupId);
            if (jobs.isEmpty()) {
                return false;
            }
            // All jobs of a group share the participation and the push, so any one of them describes the build.
            BuildJob buildJob = jobs.getFirst();
            // A job that is still missing is one the query found to be retried no more, so its container is lost.
            List<BuildJob> lostJobs = jobs.stream().filter(job -> job.getBuildStatus() == BuildStatus.MISSING).toList();
            if (!lostJobs.isEmpty() && isReplacedByLaterBuildOfSameCommit(buildJob)) {
                closeLostJobs(lostJobs, BuildStatus.CANCELLED);
                jobs.stream().map(BuildJob::getResult).filter(Objects::nonNull).map(Result::getId).distinct().forEach(programmingExerciseGradingService::discardContainerResult);
                log.info("Deleted the aggregated result of build group {}, which lost a container and was replaced by a later build of the same commit", buildGroupId);
                broadcastFinalizedBuildGroup(buildGroupId);
                return false;
            }
            Optional<Participation> participationOptional = participationRepository.findWithProgrammingExerciseById(buildJob.getParticipationId());
            if (participationOptional.isEmpty()) {
                log.warn("Participation with id {} of build group {} has been deleted. The group is not finalized.", buildJob.getParticipationId(), buildGroupId);
                return false;
            }
            ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();
            if (participation.getProgrammingExercise() == null) {
                participation.setProgrammingExercise(programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation));
            }
            closeLostJobs(lostJobs, BuildStatus.ERROR);
            // A job row does not store the expected count; every job of the group has finished or was closed as lost
            // above, so their number is that count.
            long finishedJobs = jobs.stream().filter(job -> FINISHED_BUILD_STATUSES.contains(job.getBuildStatus())).count();
            ZonedDateTime completionDate = jobs.stream().map(BuildJob::getBuildCompletionDate).filter(Objects::nonNull).max(Comparator.naturalOrder())
                    .orElseGet(ZonedDateTime::now);
            Result finalizedResult = finalizeIfGroupComplete(jobs, (int) finishedJobs, participation, completionDate);
            if (finalizedResult == null) {
                return false;
            }
            log.info("Finalized build group {} of participation {}, whose aggregated result had stayed in progress", buildGroupId, participation.getId());
            programmingMessagingService.notifyUserAboutNewResult(finalizedResult, participation);
            if (buildJob.getRepositoryType() == RepositoryType.SOLUTION
                    && (buildJob.getTriggeredByPushTo() == RepositoryType.TESTS || buildJob.getTriggeredByPushTo() == RepositoryType.AUXILIARY)) {
                triggerTemplateBuild(buildJob.getExerciseId(), buildJob.getBuildJobId(), testCommitHashOf(finalizedResult, buildJob), buildJob.getTriggeredByPushTo());
            }
            return true;
        }
        finally {
            aggregationLocks.unlock(buildGroupId);
        }
    }

    /** Whether a later build of the job's commit, such as the retry of a missing job, exists for its participation. */
    private boolean isReplacedByLaterBuildOfSameCommit(BuildJob buildJob) {
        if (buildJob.getParticipationId() == null || buildJob.getBuildSubmissionDate() == null) {
            return false;
        }
        return buildJobRepository.existsByParticipationIdAndCommitHashAndBuildSubmissionDateAfter(buildJob.getParticipationId(), buildJob.getCommitHash(),
                buildJob.getBuildSubmissionDate());
    }

    /**
     * Marks the lost jobs as finished, so that a result one of them still reports is not merged, and as failed to build,
     * since they never reported.
     */
    private void closeLostJobs(List<BuildJob> lostJobs, BuildStatus status) {
        if (lostJobs.isEmpty()) {
            return;
        }
        lostJobs.forEach(job -> {
            job.setBuildStatus(status);
            job.setBuildFailed(true);
        });
        buildJobRepository.saveAll(lostJobs);
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

    /**
     * Merges one container's result into its build group's aggregated result and finalizes that once the group is complete.
     *
     * @param participation  the participation that was built
     * @param buildJob       the finished build job of the container
     * @param buildResult    the build result of the container
     * @param agentBuildLogs the build logs the agent reported on the result queue item
     * @param buildStatus    the status the container's build job finished with
     * @param testsExpected  whether tests were expected for this container
     * @return the aggregated result together with the build job saved for this container
     */
    private ContainerOutcome processContainerResult(ProgrammingExerciseParticipation participation, BuildJobQueueItem buildJob, BuildResult buildResult,
            List<BuildLogDTO> agentBuildLogs, BuildStatus buildStatus, boolean testsExpected) {
        // A container whose build script failed completes normally, but the agent then reports its logs only on the queue
        // item; carry them over into the build result so they are kept with the merged result.
        final boolean logsOnlyOnQueueItem = !buildResult.hasLogs() && agentBuildLogs != null && !agentBuildLogs.isEmpty();
        final BuildResult effectiveBuildResult = logsOnlyOnQueueItem ? buildResult.withBuildLogs(agentBuildLogs) : buildResult;
        final BuildJobQueueItem.BuildGroupMembership buildGroup = buildJob.buildGroup();
        if (buildGroup == null) {
            throw new IllegalStateException("a container job must carry its build group");
        }
        final String buildGroupId = buildGroup.buildGroupId();
        final int expectedContainerCount = buildGroup.expectedContainerCount();
        // Serializes the group's containers across nodes: without it, two nodes could each create an aggregated result for
        // the group, and neither would ever reach the expected container count.
        DistributedMap<String, Boolean> aggregationLocks = distributedDataAccessService.getResultAggregationLockMap();
        aggregationLocks.lock(buildGroupId);
        try {
            // Append, link and finalize each commit on their own, as Artemis keeps transactions out of services.
            ContainerOutcome outcome = null;
            Result appendedResult = null;
            BuildJob linkedContainerJob = null;
            try {
                // A job that has already finished, because the sweep closed it as lost or its result arrived twice, is not
                // merged: that would change a result that was already reported or deleted.
                Optional<BuildJob> finishedJob = buildJobRepository.findByBuildJobId(buildJob.id()).filter(job -> FINISHED_BUILD_STATUSES.contains(job.getBuildStatus()));
                if (finishedJob.isPresent()) {
                    log.info("The result of container {} of build job {} of build group {} arrived after the job had finished and is not merged", buildGroup.containerName(),
                            buildJob.id(), buildGroupId);
                    return new ContainerOutcome(null, finishedJob.get(), false, true);
                }
                // The aggregate is found through the group's job links: a retry or re-push of the same commit opens a new
                // group, and a tutor's draft assessment on the submission must not be taken for it.
                Long aggregatedResultId = findAggregatedResultId(buildGroupId);
                AppendedContainerResult appended = programmingExerciseGradingService.appendContainerResult(participation, effectiveBuildResult, testsExpected,
                        buildGroup.containerName(), aggregatedResultId);
                if (appended != null) {
                    appendedResult = appended.result();
                    linkedContainerJob = saveFinishedBuildJob(buildJob, buildStatus, appendedResult, appended.containerFailed());
                    if (linkedContainerJob == null) {
                        // Without the link the group's count stays one short, so the recovery below records the job instead.
                        throw new IllegalStateException("the build job of container " + buildGroup.containerName() + " could not be saved after its result was merged");
                    }
                    Result finalizedResult = finalizeIfGroupComplete(buildGroupId, expectedContainerCount, participation, effectiveBuildResult.buildRunDate());
                    outcome = new ContainerOutcome(finalizedResult != null ? finalizedResult : appendedResult, linkedContainerJob, finalizedResult != null);
                }
            }
            catch (RuntimeException e) {
                log.error("Could not merge the result of container {} of build job {}", buildGroup.containerName(), buildJob.id(), e);
            }
            if (outcome != null) {
                return outcome;
            }
            if (linkedContainerJob != null) {
                // Only the finalization failed. Keep the link: the recovery below would mark a merged container as failed and,
                // for the group's first container, make the next sibling open a second aggregate. finalizeCompletedBuildGroups
                // covers a second failure.
                Result finalizedResult = null;
                try {
                    finalizedResult = finalizeIfGroupComplete(buildGroupId, expectedContainerCount, participation, effectiveBuildResult.buildRunDate());
                }
                catch (RuntimeException e) {
                    log.error("Could not finalize build group {} after container {} of build job {} merged; the group's aggregated result stays in progress", buildGroupId,
                            buildGroup.containerName(), buildJob.id(), e);
                }
                return new ContainerOutcome(finalizedResult != null ? finalizedResult : appendedResult, linkedContainerJob, finalizedResult != null);
            }
            // The merge failed: record the job as finished anyway, without a link, so the group's count still advances and, if
            // this was the last container, the siblings' aggregate is finalized now, as failed.
            BuildJob savedContainerJob = saveFinishedBuildJob(buildJob, BuildStatus.ERROR, null);
            Result finalizedResult = finalizeIfGroupComplete(buildGroupId, expectedContainerCount, participation, effectiveBuildResult.buildRunDate());
            return new ContainerOutcome(finalizedResult, savedContainerJob, finalizedResult != null);
        }
        finally {
            aggregationLocks.unlock(buildGroupId);
        }
    }

    private Long findAggregatedResultId(String buildGroupId) {
        return buildJobRepository.findResultIdsOfBuildGroup(buildGroupId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    }

    private Result finalizeIfGroupComplete(String buildGroupId, int expectedContainerCount, ProgrammingExerciseParticipation participation, ZonedDateTime completionDate) {
        return finalizeIfGroupComplete(buildJobRepository.findAllByBuildGroupId(buildGroupId), expectedContainerCount, participation, completionDate);
    }

    /** Finalizes the aggregated result of a build group once every job of the group has finished. */
    private Result finalizeIfGroupComplete(List<BuildJob> jobs, int expectedContainerCount, ProgrammingExerciseParticipation participation, ZonedDateTime completionDate) {
        long finishedContainers = jobs.stream().filter(job -> FINISHED_BUILD_STATUSES.contains(job.getBuildStatus())).count();
        if (finishedContainers < expectedContainerCount) {
            return null;
        }
        Long aggregatedResultId = jobs.stream().map(BuildJob::getResult).filter(Objects::nonNull).map(Result::getId).findFirst().orElse(null);
        if (aggregatedResultId == null) {
            return null;
        }
        // A job without a result link did not merge. The fallback save in processResult keeps the agent's status, so such a
        // job can be SUCCESSFUL, which the status check alone would miss.
        boolean allJobsSucceeded = jobs.stream().allMatch(job -> job.getBuildStatus() == BuildStatus.SUCCESSFUL && job.getResult() != null);
        boolean anyContainerFailedToBuild = jobs.stream().anyMatch(BuildJob::isBuildFailed);
        Result finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultId, participation, allJobsSucceeded, anyContainerFailedToBuild,
                completionDate);
        broadcastFinalizedBuildGroup(jobs.getFirst().getBuildGroupId());
        return finalizedResult;
    }

    /**
     * Sends the jobs of a closed build group to the build overview again, as each was announced while the aggregated
     * result was still in progress.
     */
    private void broadcastFinalizedBuildGroup(String buildGroupId) {
        localCIQueueWebsocketService.ifPresent(service -> {
            try {
                buildJobRepository.findWithDataByBuildGroupId(buildGroupId).forEach(buildJob -> service.sendChangedFinishedBuildJobOverWebsocket(FinishedBuildJobDTO.of(buildJob)));
            }
            catch (Exception e) {
                log.warn("Could not send the finished build jobs of build group {} over WebSocket after its result was finalized", buildGroupId, e);
            }
        });
    }

    /** What processing one container produced; {@code arrivedTooLate} marks a result skipped because its job had already finished. */
    private record ContainerOutcome(Result result, BuildJob savedBuildJob, boolean completed, boolean arrivedTooLate) {

        ContainerOutcome(Result result, BuildJob savedBuildJob, boolean completed) {
            this(result, savedBuildJob, completed, false);
        }
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
        return saveFinishedBuildJob(queueItem, buildStatus, result, false);
    }

    private BuildJob saveFinishedBuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, Result result, boolean buildFailed) {
        try {
            BuildJob buildJob = new BuildJob(queueItem, buildStatus, result, buildFailed);
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
