package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

/**
 * Unit tests for the joint {@code waitForBuildResults} (performance lever B1): the solution and template builds
 * are triggered together and awaited under a single shared timeout, with each result attributed to its own
 * repository type by its own participation and freshness bound.
 */
class VariantBuildVerificationServiceJointWaitTest {

    private static final long SOLUTION_PARTICIPATION_ID = 10L;

    private static final long TEMPLATE_PARTICIPATION_ID = 20L;

    private ResultTestRepository resultRepository;

    private TemplateProgrammingExerciseParticipationTestRepository templateRepository;

    private SolutionProgrammingExerciseParticipationRepository solutionRepository;

    private VariantBuildVerificationService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        templateRepository = mock(TemplateProgrammingExerciseParticipationTestRepository.class);
        solutionRepository = mock(SolutionProgrammingExerciseParticipationRepository.class);
        ProgrammingSubmissionRepository submissionRepository = mock(ProgrammingSubmissionRepository.class);
        resultRepository = mock(ResultTestRepository.class);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);

        SolutionProgrammingExerciseParticipation solutionParticipation = mock(SolutionProgrammingExerciseParticipation.class);
        when(solutionParticipation.getId()).thenReturn(SOLUTION_PARTICIPATION_ID);
        TemplateProgrammingExerciseParticipation templateParticipation = mock(TemplateProgrammingExerciseParticipation.class);
        when(templateParticipation.getId()).thenReturn(TEMPLATE_PARTICIPATION_ID);
        when(solutionRepository.findByProgrammingExerciseId(42L)).thenReturn(Optional.of(solutionParticipation));
        when(templateRepository.findByProgrammingExerciseId(42L)).thenReturn(Optional.of(templateParticipation));

        service = new VariantBuildVerificationService(templateRepository, solutionRepository, submissionRepository, resultRepository, mock(GitService.class),
                mock(ContinuousIntegrationTriggerService.class), mock(ProgrammingExerciseParticipationService.class), mock(ProgrammingFeedbackSynthesizerService.class));
    }

    private Map<RepositoryType, PendingBuild> pending() {
        Instant triggeredAt = Instant.now().minusSeconds(60);
        Map<RepositoryType, PendingBuild> pending = new EnumMap<>(RepositoryType.class);
        pending.put(RepositoryType.SOLUTION, new PendingBuild("solution-hash", triggeredAt));
        pending.put(RepositoryType.TEMPLATE, new PendingBuild("template-hash", triggeredAt));
        return pending;
    }

    private Result freshResult(double score, Integer testCaseCount) {
        Result result = mock(Result.class);
        when(result.getScore()).thenReturn(score);
        when(result.getTestCaseCount()).thenReturn(testCaseCount);
        when(result.getCompletionDate()).thenReturn(ZonedDateTime.now());
        return result;
    }

    private void stubResult(long participationId, Result result) {
        when(resultRepository.findFirstWithSubmissionAndFeedbacksByParticipationIdOrderByCompletionDateDesc(participationId)).thenReturn(Optional.of(result));
    }

    @Test
    void shouldReportBothSuccessesWhenSolutionPassesAndTemplateFailsAsRequired() throws Exception {
        stubResult(SOLUTION_PARTICIPATION_ID, freshResult(100.0, 5));
        stubResult(TEMPLATE_PARTICIPATION_ID, freshResult(0.0, 5));

        Map<RepositoryType, BuildResultOutcome> outcomes = service.waitForBuildResults(exercise, pending());

        assertThat(outcomes.get(RepositoryType.SOLUTION).state()).isEqualTo(BuildResultState.SUCCESS);
        assertThat(outcomes.get(RepositoryType.TEMPLATE).state()).isEqualTo(BuildResultState.SUCCESS);
    }

    @Test
    void shouldAttributeAFailingSolutionAndPassingTemplateSeparately() throws Exception {
        // Solution below 100% does not reach its target; template at 0% with tests does.
        stubResult(SOLUTION_PARTICIPATION_ID, freshResult(80.0, 5));
        stubResult(TEMPLATE_PARTICIPATION_ID, freshResult(0.0, 5));

        Map<RepositoryType, BuildResultOutcome> outcomes = service.waitForBuildResults(exercise, pending());

        assertThat(outcomes.get(RepositoryType.SOLUTION).state()).isEqualTo(BuildResultState.FAILED);
        assertThat(outcomes.get(RepositoryType.TEMPLATE).state()).isEqualTo(BuildResultState.SUCCESS);
    }

    @Test
    void shouldReportParticipationNotFoundWithoutBlockingTheOtherBuild() throws Exception {
        // No solution participation exists: that type resolves to PARTICIPATION_NOT_FOUND immediately, while the
        // template still returns its fresh result.
        SolutionProgrammingExerciseParticipationRepository emptySolutionRepository = mock(SolutionProgrammingExerciseParticipationRepository.class);
        when(emptySolutionRepository.findByProgrammingExerciseId(42L)).thenReturn(Optional.empty());
        stubResult(TEMPLATE_PARTICIPATION_ID, freshResult(0.0, 5));

        service = new VariantBuildVerificationService(templateRepository, emptySolutionRepository, mock(ProgrammingSubmissionRepository.class), resultRepository,
                mock(GitService.class), mock(ContinuousIntegrationTriggerService.class), mock(ProgrammingExerciseParticipationService.class),
                mock(ProgrammingFeedbackSynthesizerService.class));

        Map<RepositoryType, BuildResultOutcome> outcomes = service.waitForBuildResults(exercise, pending());

        assertThat(outcomes.get(RepositoryType.SOLUTION).state()).isEqualTo(BuildResultState.PARTICIPATION_NOT_FOUND);
        assertThat(outcomes.get(RepositoryType.TEMPLATE).state()).isEqualTo(BuildResultState.SUCCESS);
    }

    /**
     * Feedback synthesis only renders the per-test summary fed back to the agent. When it throws, the result is
     * still there — letting the exception reach the polling catch would keep polling a result that has already
     * arrived until the shared timeout turns a green build into TIMED_OUT.
     */
    @Test
    void shouldStillReturnTheFreshResultWhenFeedbackSynthesisThrows() throws Exception {
        ProgrammingFeedbackSynthesizerService throwingSynthesizer = mock(ProgrammingFeedbackSynthesizerService.class);
        doThrow(new IllegalStateException("synthesis blew up")).when(throwingSynthesizer).attachSynthesizedFeedback(any(), any(), anyBoolean());
        service = new VariantBuildVerificationService(templateRepository, solutionRepository, mock(ProgrammingSubmissionRepository.class), resultRepository, mock(GitService.class),
                mock(ContinuousIntegrationTriggerService.class), mock(ProgrammingExerciseParticipationService.class), throwingSynthesizer);
        stubResult(SOLUTION_PARTICIPATION_ID, freshResult(100.0, 5));
        stubResult(TEMPLATE_PARTICIPATION_ID, freshResult(0.0, 5));

        Map<RepositoryType, BuildResultOutcome> outcomes = service.waitForBuildResults(exercise, pending());

        assertThat(outcomes.get(RepositoryType.SOLUTION).state()).isEqualTo(BuildResultState.SUCCESS);
        assertThat(outcomes.get(RepositoryType.TEMPLATE).state()).isEqualTo(BuildResultState.SUCCESS);
        // The single-build poll shares the helper and must behave the same.
        assertThat(service.waitForBuildResult(exercise, "solution-hash", RepositoryType.SOLUTION, Instant.now().minusSeconds(60)).state()).isEqualTo(BuildResultState.SUCCESS);
    }

    /**
     * A mismatched commit hash must never change the accept/reject outcome — this class matches by PARTICIPATION
     * + freshness on purpose (see the class-level javadoc): once a TEST-type submission exists for the current
     * tests commit, Artemis attaches every subsequent build result to THAT submission rather than one carrying
     * the just-triggered commit's own hash, so a hard hash check would reject genuinely-correct results. The
     * mismatch is only logged, never rejected.
     */
    @Test
    void shouldStillAcceptAFreshResultWhoseSubmissionCommitDiffersFromTheTriggeredOne() throws Exception {
        Result solutionResult = freshResult(100.0, 5);
        ProgrammingSubmission submission = mock(ProgrammingSubmission.class);
        when(submission.getCommitHash()).thenReturn("a-completely-different-commit");
        when(solutionResult.getSubmission()).thenReturn(submission);
        stubResult(SOLUTION_PARTICIPATION_ID, solutionResult);
        stubResult(TEMPLATE_PARTICIPATION_ID, freshResult(0.0, 5));

        Map<RepositoryType, BuildResultOutcome> outcomes = service.waitForBuildResults(exercise, pending());

        assertThat(outcomes.get(RepositoryType.SOLUTION).state()).isEqualTo(BuildResultState.SUCCESS);
    }
}
