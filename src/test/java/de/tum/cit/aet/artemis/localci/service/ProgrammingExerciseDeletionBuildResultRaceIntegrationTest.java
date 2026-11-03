package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.localvc.service.RepositoryVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;

/**
 * Deleting a programming exercise while the build system still delivers results for it.
 * <p>
 * A programming exercise cannot be deleted in one transaction, so a build result can be written after the results of the template and solution participations were
 * deleted and before the exercise row is removed. The result then references the exercise and the deletion fails with a foreign key violation (E2E "Exam test run" failed
 * on this with {@code fk_result_exercise_id} when its exam was deleted while the template and solution builds of the exercise were still being processed).
 * <p>
 * The interleaving is forced with {@link RepositoryVcsAccessTokenService#deleteByExerciseId}, which the deletion calls after it has started and before it removes the
 * exercise row.
 */
class ProgrammingExerciseDeletionBuildResultRaceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "deletionbuildrace";

    @Autowired
    private ExerciseDeletionService exerciseDeletionService;

    @Autowired
    private DistributedDataProvider distributedDataProvider;

    @Autowired
    private ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    // The service is lazy and registers its listener on the result queue when it is created, so it has to be requested for results to be processed at all.
    @Autowired
    private LocalCIResultProcessingService localCIResultProcessingService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    @AfterEach
    void clearResultQueues() {
        // A result worker can defer an item after polling it, so finish submitted work before clearing either queue.
        ThreadPoolExecutor executor = (ThreadPoolExecutor) ReflectionTestUtils.getField(localCIResultProcessingService, "resultProcessingExecutor");
        await().atMost(Duration.ofSeconds(30)).until(() -> executor.getActiveCount() == 0 && executor.getQueue().isEmpty());
        // This lane shares its queues between classes. Each explicit processing call polls only one item,
        // so a result left by another test must not consume the trigger intended for this test's race.
        distributedDataAccessService.getDistributedBuildResultQueue().clear();
        distributedDataAccessService.getDistributedDeferredBuildResultQueue().clear();
    }

    private BuildResult buildResult() {
        String branch = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise.getId()).getBranch();
        return new BuildResult(branch, "template-commit", "tests-commit", List.of(), true);
    }

    private BuildJobQueueItem templateBuildJob(String buildJobId) {
        return new BuildJobQueueItem(buildJobId, buildJobId, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), templateParticipation.getId(), course.getId(),
                programmingExercise.getId(), 0, 1, BuildStatus.BUILDING,
                new RepositoryInfo("repo", RepositoryType.TEMPLATE, RepositoryType.TEMPLATE, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now(), null, 60),
                new BuildConfig(null, "ghcr.io/example/image:1", "commit", "commit", "test-commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null,
                null);
    }

    /**
     * A result that evades the deletion marker, as one does that was already being processed when the deletion started, still must not block the deletion.
     */
    @Test
    void aResultWrittenWhileTheExerciseIsBeingDeletedDoesNotMakeTheDeletionFail() {
        long exerciseId = programmingExercise.getId();
        AtomicBoolean resultWritten = new AtomicBoolean();
        doAnswer(invocation -> {
            // Called directly instead of through the result queue so that the marker cannot keep the result out: this is the build that was past the check already.
            assertThat(programmingExerciseGradingService.processNewProgrammingExerciseResult(templateParticipation, buildResult())).isNotNull();
            resultWritten.set(resultRepository.existsByExerciseId(exerciseId));
            return invocation.callRealMethod();
        }).when(repositoryVcsAccessTokenService).deleteByExerciseId(exerciseId);

        exerciseDeletionService.delete(exerciseId, false);

        assertThat(resultWritten).as("the interleaving was forced: the result existed when the exercise was about to be removed").isTrue();
        assertThat(programmingExerciseRepository.findById(exerciseId)).isEmpty();
        assertThat(resultRepository.existsByExerciseId(exerciseId)).isFalse();
    }

    /**
     * The result of a build that finishes while the exercise is being deleted is taken from the queue as usual, but it is dropped instead of being graded.
     */
    @Test
    void aBuildResultThatIsProcessedWhileTheExerciseIsBeingDeletedIsDropped() {
        long exerciseId = programmingExercise.getId();
        String buildJobId = "deletion-race-job";
        long templateParticipationId = templateParticipation.getId();
        AtomicBoolean markerSeen = new AtomicBoolean();
        doAnswer(invocation -> {
            markerSeen.set(programmingExerciseDeletionService.isBeingDeleted(exerciseId));
            distributedDataAccessService.getDistributedBuildResultQueue().add(new ResultQueueItem(buildResult(), templateBuildJob(buildJobId), List.of(), null));
            processQueuedResults();
            // Keep deletion's permits held until this result, rather than an older queued result, has been denied admission.
            await().atMost(Duration.ofSeconds(30)).until(() -> distributedDataAccessService.getDistributedDeferredBuildResultQueue().getAll().stream()
                    .anyMatch(item -> item.buildJobQueueItem().id().equals(buildJobId)));
            return invocation.callRealMethod();
        }).when(repositoryVcsAccessTokenService).deleteByExerciseId(exerciseId);

        exerciseDeletionService.delete(exerciseId, false);

        // Scheduling is disabled; each poll may consume an earlier result rather than this test's job.
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            localCIResultProcessingService.processResultAsync();
            localCIResultProcessingService.processDeferredResultAsync();
            return buildJobRepository.findByBuildJobId(buildJobId).isPresent();
        });
        assertThat(markerSeen).as("the marker is set while the deletion runs").isTrue();
        verify(programmingExerciseGradingService, never()).processNewProgrammingExerciseResult(
                argThat(participation -> participation != null && participation.getId().equals(templateParticipationId)), any(BuildResult.class), anyBoolean());
        assertThat(buildJobRepository.findByBuildJobId(buildJobId).orElseThrow().getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(programmingExerciseRepository.findById(exerciseId)).isEmpty();
        assertThat(resultRepository.existsByExerciseId(exerciseId)).isFalse();
    }

    @Test
    void anAdmittedResultAndFinalDeletionExcludeEachOther() throws Exception {
        long exerciseId = programmingExercise.getId();
        String buildJobId = "admitted-deletion-race-job";
        long templateParticipationId = templateParticipation.getId();
        CountDownLatch admitted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        AtomicBoolean deletionLockSeen = new AtomicBoolean();
        doAnswer(invocation -> {
            admitted.countDown();
            assertThat(allowPersistence.await(30, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(programmingExerciseGradingService).processNewProgrammingExerciseResult(
                argThat(participation -> participation != null && participation.getId().equals(templateParticipationId)), any(BuildResult.class), anyBoolean());

        try (var workers = Executors.newCachedThreadPool()) {
            doAnswer(invocation -> {
                // Probe shared admission from another thread while final cleanup holds every permit.
                boolean acquired = workers.submit(() -> {
                    for (int slot = 0; slot < 256; slot++) {
                        var lock = distributedDataProvider.getLock("programming-exercise-result-processing-" + exerciseId + "-permit-" + slot);
                        boolean available = lock.tryLock(Duration.ZERO);
                        if (available) {
                            lock.unlock();
                        }
                        assertThat(available).as("deletion owns writer permit %s", slot).isFalse();
                    }
                    return programmingExerciseDeletionService.tryAdmitResultProcessing(exerciseId).isPresent();
                }).get(10, TimeUnit.SECONDS);
                deletionLockSeen.set(!acquired);
                assertThat(acquired).as("final cleanup must exclude result persistence").isFalse();
                return invocation.callRealMethod();
            }).when(repositoryVcsAccessTokenService).deleteByExerciseId(exerciseId);

            try {
                distributedDataAccessService.getDistributedBuildResultQueue().add(new ResultQueueItem(buildResult(), templateBuildJob(buildJobId), List.of(), null));
                localCIResultProcessingService.processResultAsync();
                await().atMost(Duration.ofSeconds(30)).until(() -> {
                    localCIResultProcessingService.processResultAsync();
                    localCIResultProcessingService.processDeferredResultAsync();
                    return admitted.getCount() == 0;
                });
                var deletion = workers.submit(() -> SecurityUtils.runAsSystem(() -> exerciseDeletionService.delete(exerciseId, false)));
                await().atMost(Duration.ofSeconds(10)).until(() -> programmingExerciseDeletionService.isBeingDeleted(exerciseId));
                verify(repositoryVcsAccessTokenService, never()).deleteByExerciseId(exerciseId);
                allowPersistence.countDown();
                deletion.get(30, TimeUnit.SECONDS);
                assertThat(deletionLockSeen).isTrue();
                assertThat(buildJobRepository.findByBuildJobId(buildJobId).orElseThrow().getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
                assertThat(programmingExerciseRepository.findById(exerciseId)).isEmpty();
                assertThat(resultRepository.existsByExerciseId(exerciseId)).isFalse();
            }
            finally {
                allowPersistence.countDown();
                await().atMost(Duration.ofSeconds(30)).until(() -> {
                    localCIResultProcessingService.processResultAsync();
                    localCIResultProcessingService.processDeferredResultAsync();
                    return buildJobRepository.findByBuildJobId(buildJobId).isPresent();
                });
            }
        }
    }

    private void processQueuedResults() {
        // Earlier tests can enqueue results before the lazy consumer registers its listener. One new-item event only consumes one of those old results.
        // Invoke the real bounded sweep without registering its recurring @Scheduled task in the shared context.
        new LocalCIResultListenerService(distributedDataAccessService, localCIResultProcessingService).processQueuedResults();
    }

    /**
     * If the deletion fails the exercise stays, and its builds must be graded again.
     */
    @Test
    void theMarkerIsRemovedWhenTheDeletionFails() {
        long exerciseId = programmingExercise.getId();
        doAnswer(invocation -> {
            throw new IllegalStateException("the deletion fails after it has started");
        }).when(repositoryVcsAccessTokenService).deleteByExerciseId(exerciseId);

        assertThatThrownBy(() -> exerciseDeletionService.delete(exerciseId, false)).isInstanceOf(IllegalStateException.class);

        assertThat(programmingExerciseDeletionService.isBeingDeleted(exerciseId)).isFalse();
        assertThat(programmingExerciseRepository.findById(exerciseId)).isPresent();
    }
}
