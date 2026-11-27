package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.localci.service.ProgrammingExerciseFeedbackCreationService.MergedBuildRun;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService.AppendedContainerResult;

/**
 * Merges the results of the containers of a multi-container build into the aggregated result of their build group, and
 * finalizes it once the group is complete. Each container runs as an ordinary build job; LocalCIResultProcessingService
 * hands its result over here. The sweep closes the groups that no container will complete any more.
 */
@Profile(PROFILE_LOCALCI)
@Lazy
@Service
public class LocalCIBuildGroupService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIBuildGroupService.class);

    static final Set<BuildStatus> FINISHED_BUILD_STATUSES = Set.of(BuildStatus.SUCCESSFUL, BuildStatus.FAILED, BuildStatus.ERROR, BuildStatus.CANCELLED, BuildStatus.TIMEOUT);

    /** How long the sweep leaves a group whose last job just finished to that container's own finalization. */
    private static final Duration COMPLETED_GROUP_GRACE_PERIOD = Duration.ofMinutes(2);

    private static final int COMPLETED_GROUPS_PER_SWEEP = 50;

    /** How far back the sweep looks: a group is closed within minutes, or within the missing-job retry window once it lost a container. */
    private static final Duration SWEEP_LOOKBACK = Duration.ofDays(1);

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final BuildJobRepository buildJobRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ParticipationRepository participationRepository;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    private final Optional<LocalCIQueueWebsocketService> localCIQueueWebsocketService;

    private final LocalCIFinishedBuildJobService finishedBuildJobService;

    public LocalCIBuildGroupService(ProgrammingExerciseGradingService programmingExerciseGradingService, BuildJobRepository buildJobRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ParticipationRepository participationRepository, DistributedDataAccessService distributedDataAccessService,
            ProgrammingExerciseDeletionService programmingExerciseDeletionService, Optional<LocalCIQueueWebsocketService> localCIQueueWebsocketService,
            LocalCIFinishedBuildJobService finishedBuildJobService) {
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.buildJobRepository = buildJobRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationRepository = participationRepository;
        this.distributedDataAccessService = distributedDataAccessService;
        this.programmingExerciseDeletionService = programmingExerciseDeletionService;
        this.localCIQueueWebsocketService = localCIQueueWebsocketService;
        this.finishedBuildJobService = finishedBuildJobService;
    }

    /** Reports the result the sweep finalized for a build group. */
    @FunctionalInterface
    interface FinalizedBuildGroupHandler {

        /**
         * @param finalizedResult the finalized aggregated result
         * @param participation   the participation that was built
         * @param buildJob        a job of the group, which describes the build: all of them share the participation and the push
         */
        void finalized(Result finalizedResult, ProgrammingExerciseParticipation participation, BuildJob buildJob);
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
     * @param onFinalized             reports a group's finalized result, while the group is still locked
     * @return the number of build groups finalized by this run
     */
    int finalizeCompletedBuildGroups(int maxMissingJobRetries, ZonedDateTime missingJobsRetriedSince, FinalizedBuildGroupHandler onFinalized) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        List<String> buildGroupIds = buildJobRepository.findBuildGroupsWithResultInProgressAndNoRunningJob(FINISHED_BUILD_STATUSES, now.minus(SWEEP_LOOKBACK),
                now.minus(COMPLETED_GROUP_GRACE_PERIOD), PageRequest.of(0, COMPLETED_GROUPS_PER_SWEEP));
        int finalizedGroups = 0;
        for (String buildGroupId : buildGroupIds) {
            try {
                if (finalizeCompletedBuildGroupIfAdmitted(buildGroupId, maxMissingJobRetries, missingJobsRetriedSince, onFinalized)) {
                    finalizedGroups++;
                }
            }
            catch (RuntimeException e) {
                log.error("Could not finalize the complete build group {} whose aggregated result stayed in progress", buildGroupId, e);
            }
        }
        return finalizedGroups;
    }

    /**
     * Finalizes a complete build group under its exercise's result-processing admission, which the result processing
     * also takes before a group's lock, so the sweep does not write a result while the exercise is being deleted. A group
     * that is not admitted now is left for the next sweep.
     */
    private boolean finalizeCompletedBuildGroupIfAdmitted(String buildGroupId, int maxMissingJobRetries, ZonedDateTime missingJobsRetriedSince,
            FinalizedBuildGroupHandler onFinalized) {
        Long exerciseId = buildJobRepository.findExerciseIdOfBuildGroup(buildGroupId);
        if (exerciseId == null) {
            return false;
        }
        var admission = programmingExerciseDeletionService.tryAdmitResultProcessing(exerciseId);
        if (admission.isEmpty()) {
            log.debug("Not finalizing build group {} now: the result processing of exercise {} is not admitted", buildGroupId, exerciseId);
            return false;
        }
        try (var _ = admission.get()) {
            return finalizeCompletedBuildGroup(buildGroupId, maxMissingJobRetries, missingJobsRetriedSince, onFinalized);
        }
    }

    private boolean finalizeCompletedBuildGroup(String buildGroupId, int maxMissingJobRetries, ZonedDateTime missingJobsRetriedSince, FinalizedBuildGroupHandler onFinalized) {
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
            // A missing job can be picked up again after the sweep's query, which changes its status without this lock. Its
            // container is running, so the group stays open for it.
            if (jobs.stream().anyMatch(job -> !FINISHED_BUILD_STATUSES.contains(job.getBuildStatus()) && job.getBuildStatus() != BuildStatus.MISSING)) {
                log.info("Not closing build group {}: a job of the group is queued or building again", buildGroupId);
                return false;
            }
            if (jobs.stream().anyMatch(job -> isMissingJobStillRetried(job, maxMissingJobRetries, missingJobsRetriedSince))) {
                log.debug("Not closing build group {}: a missing job of the group is still retried", buildGroupId);
                return false;
            }
            // All jobs of a group share the participation and the push, so any one of them describes the build.
            BuildJob buildJob = jobs.getFirst();
            // A job that is still missing is not retried any more, so its container is lost.
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
            // A job row does not store the expected count, but every job of a group is saved before any is published, so
            // their number is that count.
            ZonedDateTime completionDate = jobs.stream().map(BuildJob::getBuildCompletionDate).filter(Objects::nonNull).max(Comparator.naturalOrder())
                    .orElseGet(ZonedDateTime::now);
            // the sweep has no container result, so the revisions the build ran are unknown
            Result finalizedResult = finalizeIfGroupComplete(jobs, jobs.size(), participation, completionDate, null);
            if (finalizedResult == null) {
                return false;
            }
            log.info("Finalized build group {} of participation {}, whose aggregated result had stayed in progress", buildGroupId, participation.getId());
            onFinalized.finalized(finalizedResult, participation, buildJob);
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
     * Whether LocalCIMissingJobService will still retry a job: it is missing, has retries left, lies within the retry window
     * and no newer job of its participation exists, as BuildJobRepository#findMissingJobsToRetryInTimeRange selects them.
     */
    private boolean isMissingJobStillRetried(BuildJob job, int maxMissingJobRetries, ZonedDateTime missingJobsRetriedSince) {
        ZonedDateTime submissionDate = job.getBuildSubmissionDate();
        return job.getBuildStatus() == BuildStatus.MISSING && job.getRetryCount() < maxMissingJobRetries && submissionDate != null
                && !submissionDate.isBefore(missingJobsRetriedSince)
                && !buildJobRepository.existsByParticipationIdAndBuildSubmissionDateAfter(job.getParticipationId(), submissionDate);
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
    ContainerOutcome processContainerResult(ProgrammingExerciseParticipation participation, BuildJobQueueItem buildJob, BuildResult buildResult, List<BuildLogDTO> agentBuildLogs,
            BuildStatus buildStatus, boolean testsExpected) {
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
            LinkedContainer linked = null;
            // A container whose result could not be merged is missing from the merged result, which a student must be
            // able to tell apart from tests that failed, so it counts as failed to build unless its result was merged.
            boolean unmergedContainerFailed = true;
            try {
                // A job that has already finished, because the sweep closed it as lost or its result arrived twice, is not
                // merged: that would change a result that was already reported or deleted.
                Optional<BuildJob> finishedJob = buildJobRepository.findByBuildJobId(buildJob.id()).filter(job -> FINISHED_BUILD_STATUSES.contains(job.getBuildStatus()));
                if (finishedJob.isPresent()) {
                    log.info("The result of container {} of build job {} of build group {} arrived after the job had finished and is not merged", buildGroup.containerName(),
                            buildJob.id(), buildGroupId);
                    return new ContainerOutcome(null, finishedJob.get(), false, true);
                }
                linked = appendAndLinkContainer(participation, buildJob, buildGroup, effectiveBuildResult, buildStatus, testsExpected, aggregationLocks);
                if (linked != null) {
                    Result finalizedResult = finalizeIfGroupComplete(buildGroupId, expectedContainerCount, participation, effectiveBuildResult.buildRunDate(),
                            effectiveBuildResult);
                    return new ContainerOutcome(finalizedResult != null ? finalizedResult : linked.appendedResult(), linked.job(), finalizedResult != null);
                }
            }
            catch (UnlinkedContainerException e) {
                log.error("Could not save the build job of container {} of build job {} after its result was merged", buildGroup.containerName(), buildJob.id(), e);
                unmergedContainerFailed = e.containerFailed();
            }
            catch (RuntimeException e) {
                log.error("Could not merge the result of container {} of build job {}", buildGroup.containerName(), buildJob.id(), e);
            }
            if (linked != null) {
                return finalizeAfterAFailedFinalization(linked, buildJob, buildGroup, effectiveBuildResult, participation);
            }
            return recordUnmergedContainer(buildJob, buildGroup, effectiveBuildResult, participation, unmergedContainerFailed);
        }
        finally {
            aggregationLocks.unlock(buildGroupId);
        }
    }

    /**
     * Appends a container's result to its group's aggregated result, which the group's first container creates, and links
     * the container's job to it.
     *
     * @return the aggregated result and the linked job, or null if the result could not be appended
     */
    private @Nullable LinkedContainer appendAndLinkContainer(ProgrammingExerciseParticipation participation, BuildJobQueueItem buildJob,
            BuildJobQueueItem.BuildGroupMembership buildGroup, BuildResult buildResult, BuildStatus buildStatus, boolean testsExpected,
            DistributedMap<String, Boolean> aggregationLocks) {
        // The aggregate is found through the group's job links: a retry or re-push of the same commit opens a new
        // group, and a tutor's draft assessment on the submission must not be taken for it.
        Long aggregatedResultId = findAggregatedResultId(buildGroup.buildGroupId());
        // The container that creates the aggregate holds the participation's lock until its job links to it, so
        // that the cleanup in a finalization never takes the unlinked aggregate for an abandoned one. Every container of a
        // solution build holds it too: it updates the exercise's test cases only if no newer build has finalized, and a
        // finalization of a newer build must not reconcile them between that check and the update.
        boolean holdsParticipationLock = aggregatedResultId == null || participation instanceof SolutionProgrammingExerciseParticipation;
        String participationLock = holdsParticipationLock ? participationLockKey(participation.getId()) : null;
        if (participationLock != null) {
            aggregationLocks.lock(participationLock);
        }
        try {
            AppendedContainerResult appended = programmingExerciseGradingService.appendContainerResult(participation, buildResult, testsExpected, buildGroup.containerName(),
                    aggregatedResultId, buildJob.jobTimingInfo().submissionDate());
            if (appended == null) {
                return null;
            }
            BuildJob linkedJob = finishedBuildJobService.saveFinishedBuildJob(buildJob, buildStatus, appended.result(), appended.containerFailed(), ranClean(buildResult));
            if (linkedJob == null) {
                // Without the link the group's count stays one short, so the recovery records the job instead.
                throw new UnlinkedContainerException(buildGroup.containerName(), appended.containerFailed());
            }
            return new LinkedContainer(appended.result(), linkedJob);
        }
        finally {
            if (participationLock != null) {
                aggregationLocks.unlock(participationLock);
            }
        }
    }

    /**
     * Only the finalization of a merged container failed. The link is kept: recording the container as unmerged would mark it
     * as failed and, for the group's first container, make the next sibling open a second aggregate.
     * finalizeCompletedBuildGroups covers a second failure.
     */
    private ContainerOutcome finalizeAfterAFailedFinalization(LinkedContainer linked, BuildJobQueueItem buildJob, BuildJobQueueItem.BuildGroupMembership buildGroup,
            BuildResult buildResult, ProgrammingExerciseParticipation participation) {
        Result finalizedResult = null;
        try {
            finalizedResult = finalizeIfGroupComplete(buildGroup.buildGroupId(), buildGroup.expectedContainerCount(), participation, buildResult.buildRunDate(), buildResult);
        }
        catch (RuntimeException e) {
            log.error("Could not finalize build group {} after container {} of build job {} merged; the group's aggregated result stays in progress", buildGroup.buildGroupId(),
                    buildGroup.containerName(), buildJob.id(), e);
        }
        return new ContainerOutcome(finalizedResult != null ? finalizedResult : linked.appendedResult(), linked.job(), finalizedResult != null);
    }

    /**
     * The merge failed: records the job as finished anyway, without a link, so the group's count still advances and, if this
     * was the last container, the siblings' aggregate is finalized now, as failed.
     *
     * @param containerFailed whether the container counts as failed to build, which marks the merged result's submission as a
     *                            failed build: true unless the container's result was merged and only its job could not be saved
     */
    private ContainerOutcome recordUnmergedContainer(BuildJobQueueItem buildJob, BuildJobQueueItem.BuildGroupMembership buildGroup, BuildResult buildResult,
            ProgrammingExerciseParticipation participation, boolean containerFailed) {
        BuildJob savedContainerJob = finishedBuildJobService.saveFinishedBuildJob(buildJob, BuildStatus.ERROR, null, containerFailed, false);
        Result finalizedResult = finalizeIfGroupComplete(buildGroup.buildGroupId(), buildGroup.expectedContainerCount(), participation, buildResult.buildRunDate(), buildResult);
        return new ContainerOutcome(finalizedResult, savedContainerJob, finalizedResult != null);
    }

    /** a container whose result was appended to the aggregated result, and its job linked to that */
    private record LinkedContainer(Result appendedResult, BuildJob job) {
    }

    /** A container's result was merged, but its job could not be saved, so the job is not linked to the merged result. */
    private static final class UnlinkedContainerException extends IllegalStateException {

        private final boolean containerFailed;

        private UnlinkedContainerException(String containerName, boolean containerFailed) {
            super("the build job of container " + containerName + " could not be saved after its result was merged");
            this.containerFailed = containerFailed;
        }

        /** @return whether the merged container failed to build */
        boolean containerFailed() {
            return containerFailed;
        }
    }

    private Long findAggregatedResultId(String buildGroupId) {
        return buildJobRepository.findResultIdsOfBuildGroup(buildGroupId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    }

    private Result finalizeIfGroupComplete(String buildGroupId, int expectedContainerCount, ProgrammingExerciseParticipation participation, ZonedDateTime completionDate,
            BuildResult lastContainerResult) {
        return finalizeIfGroupComplete(buildJobRepository.findAllByBuildGroupId(buildGroupId), expectedContainerCount, participation, completionDate, lastContainerResult);
    }

    /**
     * Whether a container's build script exited cleanly and reported no failed test, the per-container part of what a
     * build needs to complete a test case synchronization (see ProgrammingExerciseFeedbackCreationService).
     */
    private static boolean ranClean(BuildResult buildResult) {
        return Integer.valueOf(0).equals(buildResult.buildScriptExitCode()) && buildResult.jobs().stream().allMatch(job -> job.failedTests().isEmpty());
    }

    /**
     * Finalizes the aggregated result of a build group once every job of the group has finished. The last container's
     * result gives the revisions the build ran; the sweep has none.
     */
    private Result finalizeIfGroupComplete(List<BuildJob> jobs, int expectedContainerCount, ProgrammingExerciseParticipation participation, ZonedDateTime completionDate,
            @Nullable BuildResult lastContainerResult) {
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
        MergedBuildRun mergedBuildRun = lastContainerResult == null ? null
                : new MergedBuildRun(lastContainerResult.assignmentRepoCommitHash(), lastContainerResult.testsRepoCommitHash(), jobs.stream().allMatch(BuildJob::isCleanRun));
        // the finalization deletes the abandoned aggregates of the submission, which needs the participation's lock
        DistributedMap<String, Boolean> aggregationLocks = distributedDataAccessService.getResultAggregationLockMap();
        String participationLock = participationLockKey(participation.getId());
        Result finalizedResult;
        aggregationLocks.lock(participationLock);
        try {
            // Builds of the same submission are ordered by when they were triggered, not by when they finish or by result id:
            // once a newer build has finalized, an older one must not overwrite its outcome, its assessment or the
            // submission's build-failed flag, so its result is discarded instead, and an older build's result does not
            // outlive the newer build's finalization.
            ZonedDateTime triggeredAt = jobs.getFirst().getBuildSubmissionDate();
            if (triggeredAt != null && buildJobRepository.existsFinalizedBuildOfSubmissionTriggeredAfter(aggregatedResultId, triggeredAt)) {
                log.info("Discarding the aggregated result {} of build group {}, since a newer build of the same submission has already finalized", aggregatedResultId,
                        jobs.getFirst().getBuildGroupId());
                programmingExerciseGradingService.discardContainerResult(aggregatedResultId);
                finalizedResult = null;
            }
            else {
                // An older build that finalized first but created its result after this one's would stay the latest result.
                // Looked up first, since a merge into the assessment deletes this build's aggregate.
                List<Long> supersededResultIds = triggeredAt == null ? List.of()
                        : buildJobRepository.findLaterCreatedResultsOfSubmissionTriggeredBefore(aggregatedResultId, triggeredAt);
                finalizedResult = programmingExerciseGradingService.finalizeContainerResult(aggregatedResultId, participation, allJobsSucceeded, anyContainerFailedToBuild,
                        completionDate, mergedBuildRun);
                supersededResultIds.forEach(programmingExerciseGradingService::deleteSupersededResult);
            }
        }
        finally {
            aggregationLocks.unlock(participationLock);
        }
        broadcastFinalizedBuildGroup(jobs.getFirst().getBuildGroupId());
        return finalizedResult;
    }

    /**
     * The key of the lock that serializes the creation of a build group's aggregate, up to the link of its job, with the
     * cleanup of abandoned aggregates in a finalization, and a solution container's update of the test cases with the
     * finalization of a newer build. It covers every submission of the participation, since a build result is matched to
     * its submission only while it is merged. It is always taken after the lock of a build group.
     *
     * @param participationId the id of the participation
     * @return the key in the aggregation lock map
     */
    static String participationLockKey(long participationId) {
        return "participation-" + participationId;
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
    record ContainerOutcome(Result result, BuildJob savedBuildJob, boolean completed, boolean arrivedTooLate) {

        ContainerOutcome(Result result, BuildJob savedBuildJob, boolean completed) {
            this(result, savedBuildJob, completed, false);
        }
    }
}
