package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultOutcome;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultState;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.PendingBuild;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

/**
 * A wait that times out abandons its build, but CI keeps running it. If that build finishes after the next round
 * triggered its own, the straggler falls inside the new wait's freshness window and — since results are matched by
 * participation and freshness, not by commit hash — would be accepted as the new round's result, reporting on the
 * repository as it was BEFORE the repair. These tests pin that the first result after a timed-out wait is
 * discarded, on both the single and the joint wait.
 */
class VariantBuildVerificationServiceStragglerTest {

    private static final long SOLUTION_PARTICIPATION_ID = 10L;

    private static final long TEMPLATE_PARTICIPATION_ID = 20L;

    private static final long EXERCISE_ID = 42L;

    private ResultTestRepository resultRepository;

    private VariantBuildVerificationService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        TemplateProgrammingExerciseParticipationTestRepository templateRepository = mock(TemplateProgrammingExerciseParticipationTestRepository.class);
        SolutionProgrammingExerciseParticipationRepository solutionRepository = mock(SolutionProgrammingExerciseParticipationRepository.class);
        resultRepository = mock(ResultTestRepository.class);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(EXERCISE_ID);

        SolutionProgrammingExerciseParticipation solutionParticipation = mock(SolutionProgrammingExerciseParticipation.class);
        when(solutionParticipation.getId()).thenReturn(SOLUTION_PARTICIPATION_ID);
        TemplateProgrammingExerciseParticipation templateParticipation = mock(TemplateProgrammingExerciseParticipation.class);
        when(templateParticipation.getId()).thenReturn(TEMPLATE_PARTICIPATION_ID);
        when(solutionRepository.findByProgrammingExerciseId(EXERCISE_ID)).thenReturn(Optional.of(solutionParticipation));
        when(templateRepository.findByProgrammingExerciseId(EXERCISE_ID)).thenReturn(Optional.of(templateParticipation));

        service = new VariantBuildVerificationService(templateRepository, solutionRepository, mock(ProgrammingSubmissionRepository.class), resultRepository, mock(GitService.class),
                mock(ContinuousIntegrationTriggerService.class), mock(ProgrammingExerciseParticipationService.class), mock(ProgrammingFeedbackSynthesizerService.class));
    }

    /** A result completed {@code secondsAgo} ago — both are fresh against the trigger time used below. */
    private Result result(double score, int secondsAgo) {
        Result result = mock(Result.class);
        when(result.getScore()).thenReturn(score);
        when(result.getTestCaseCount()).thenReturn(5);
        when(result.getCompletionDate()).thenReturn(ZonedDateTime.now().minusSeconds(secondsAgo));
        return result;
    }

    @Test
    void discardsTheStragglerOfAnAbandonedBuildAndWaitsForThisRoundsResult() throws Exception {
        // The abandoned build reports a passing solution; this round's own build does not. Accepting the straggler
        // would verify code that was never built.
        service.noteAbandonedBuild(SOLUTION_PARTICIPATION_ID);
        // Both results are built before the stubbing chain: building a mock inside when(...) breaks the stubbing.
        Result straggler = result(100.0, 10);
        Result thisRound = result(80.0, 0);
        when(resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(SOLUTION_PARTICIPATION_ID)).thenReturn(Optional.of(straggler))
                .thenReturn(Optional.of(thisRound));

        BuildResultOutcome outcome = service.waitForBuildResult(exercise, "solution-hash", RepositoryType.SOLUTION, Instant.now().minusSeconds(60));

        assertThat(outcome.state()).isEqualTo(BuildResultState.FAILED);
    }

    @Test
    void discardsTheStragglerInTheJointWaitToo() throws Exception {
        service.noteAbandonedBuild(SOLUTION_PARTICIPATION_ID);
        Result straggler = result(100.0, 10);
        Result thisRound = result(80.0, 0);
        Result templateResult = result(0.0, 0);
        when(resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(SOLUTION_PARTICIPATION_ID)).thenReturn(Optional.of(straggler))
                .thenReturn(Optional.of(thisRound));
        // The template participation never timed out, so its first fresh result is accepted right away.
        when(resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(TEMPLATE_PARTICIPATION_ID)).thenReturn(Optional.of(templateResult));

        Map<RepositoryType, PendingBuild> pending = new EnumMap<>(RepositoryType.class);
        Instant triggeredAt = Instant.now().minusSeconds(60);
        pending.put(RepositoryType.SOLUTION, new PendingBuild("solution-hash", triggeredAt));
        pending.put(RepositoryType.TEMPLATE, new PendingBuild("template-hash", triggeredAt));

        Map<RepositoryType, BuildResultOutcome> outcomes = service.waitForBuildResults(exercise, pending);

        assertThat(outcomes.get(RepositoryType.SOLUTION).state()).isEqualTo(BuildResultState.FAILED);
        assertThat(outcomes.get(RepositoryType.TEMPLATE).state()).isEqualTo(BuildResultState.SUCCESS);
    }

    @Test
    void acceptsTheFirstResultAgainOnceTheStragglerWasDiscarded() throws Exception {
        // The mark is consumed by the wait that discards a result, so the round after it polls normally again.
        service.noteAbandonedBuild(SOLUTION_PARTICIPATION_ID);
        Result straggler = result(100.0, 10);
        Result firstRound = result(80.0, 0);
        when(resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(SOLUTION_PARTICIPATION_ID)).thenReturn(Optional.of(straggler))
                .thenReturn(Optional.of(firstRound));
        service.waitForBuildResult(exercise, "solution-hash", RepositoryType.SOLUTION, Instant.now().minusSeconds(60));

        Result nextRound = result(100.0, 0);
        when(resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(SOLUTION_PARTICIPATION_ID)).thenReturn(Optional.of(nextRound));
        BuildResultOutcome outcome = service.waitForBuildResult(exercise, "solution-hash", RepositoryType.SOLUTION, Instant.now().minusSeconds(60));

        assertThat(outcome.state()).isEqualTo(BuildResultState.SUCCESS);
    }
}
