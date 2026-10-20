package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
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
    private ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    // The service is lazy and registers its listener on the result queue when it is created, so it has to be requested for results to be processed at all.
    @Autowired
    private LocalCIResultProcessingService localCIResultProcessingService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
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
        AtomicBoolean markerSeen = new AtomicBoolean();
        doAnswer(invocation -> {
            markerSeen.set(programmingExerciseDeletionService.isBeingDeleted(exerciseId));
            distributedDataAccessService.getDistributedBuildResultQueue().add(new ResultQueueItem(buildResult(), templateBuildJob(buildJobId), List.of(), null));
            localCIResultProcessingService.processResultAsync();
            // The build job is recorded last, so once it is there the result has been taken from the queue and dealt with.
            await().atMost(Duration.ofSeconds(30)).until(() -> buildJobRepository.findByBuildJobId(buildJobId).isPresent());
            return invocation.callRealMethod();
        }).when(repositoryVcsAccessTokenService).deleteByExerciseId(exerciseId);

        exerciseDeletionService.delete(exerciseId, false);

        assertThat(markerSeen).as("the marker is set while the deletion runs").isTrue();
        verify(programmingExerciseGradingService, never()).processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean());
        assertThat(programmingExerciseRepository.findById(exerciseId)).isEmpty();
        assertThat(resultRepository.existsByExerciseId(exerciseId)).isFalse();
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
