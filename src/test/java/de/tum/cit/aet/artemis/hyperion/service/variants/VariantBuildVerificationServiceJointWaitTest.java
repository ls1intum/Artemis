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
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultOutcome;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.BuildResultState;
import de.tum.cit.aet.artemis.hyperion.service.variants.VariantBuildVerificationService.PendingBuild;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

/**
 * Unit tests for the joint {@code waitForBuildResults} (performance lever B1): the solution and template builds
 * are triggered together and awaited under a single shared timeout, with each result attributed to its own
 * repository type by its own participation and freshness bound.
 */
class VariantBuildVerificationServiceJointWaitTest {

    private static final long SOLUTION_PARTICIPATION_ID = 10L;

    private static final long TEMPLATE_PARTICIPATION_ID = 20L;

    private ResultRepository resultRepository;

    private VariantBuildVerificationService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        GitService gitService = mock(GitService.class);
        TemplateProgrammingExerciseParticipationRepository templateRepository = mock(TemplateProgrammingExerciseParticipationRepository.class);
        SolutionProgrammingExerciseParticipationRepository solutionRepository = mock(SolutionProgrammingExerciseParticipationRepository.class);
        ProgrammingSubmissionRepository submissionRepository = mock(ProgrammingSubmissionRepository.class);
        resultRepository = mock(ResultRepository.class);

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(42L);

        SolutionProgrammingExerciseParticipation solutionParticipation = mock(SolutionProgrammingExerciseParticipation.class);
        when(solutionParticipation.getId()).thenReturn(SOLUTION_PARTICIPATION_ID);
        TemplateProgrammingExerciseParticipation templateParticipation = mock(TemplateProgrammingExerciseParticipation.class);
        when(templateParticipation.getId()).thenReturn(TEMPLATE_PARTICIPATION_ID);
        when(solutionRepository.findByProgrammingExerciseId(42L)).thenReturn(Optional.of(solutionParticipation));
        when(templateRepository.findByProgrammingExerciseId(42L)).thenReturn(Optional.of(templateParticipation));

        service = new VariantBuildVerificationService(gitService, templateRepository, solutionRepository, submissionRepository, resultRepository);
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
        when(resultRepository.findFirstWithSubmissionAndFeedbacksAndTestCasesByParticipationIdOrderByCompletionDateDesc(participationId)).thenReturn(Optional.of(result));
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
        GitService gitService = mock(GitService.class);
        SolutionProgrammingExerciseParticipationRepository emptySolutionRepository = mock(SolutionProgrammingExerciseParticipationRepository.class);
        when(emptySolutionRepository.findByProgrammingExerciseId(42L)).thenReturn(Optional.empty());
        TemplateProgrammingExerciseParticipationRepository templateRepository = mock(TemplateProgrammingExerciseParticipationRepository.class);
        TemplateProgrammingExerciseParticipation templateParticipation = mock(TemplateProgrammingExerciseParticipation.class);
        when(templateParticipation.getId()).thenReturn(TEMPLATE_PARTICIPATION_ID);
        when(templateRepository.findByProgrammingExerciseId(42L)).thenReturn(Optional.of(templateParticipation));
        stubResult(TEMPLATE_PARTICIPATION_ID, freshResult(0.0, 5));

        service = new VariantBuildVerificationService(gitService, templateRepository, emptySolutionRepository, mock(ProgrammingSubmissionRepository.class), resultRepository);

        Map<RepositoryType, BuildResultOutcome> outcomes = service.waitForBuildResults(exercise, pending());

        assertThat(outcomes.get(RepositoryType.SOLUTION).state()).isEqualTo(BuildResultState.PARTICIPATION_NOT_FOUND);
        assertThat(outcomes.get(RepositoryType.TEMPLATE).state()).isEqualTo(BuildResultState.SUCCESS);
    }
}
