package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for {@link ProgrammingVariantAdapterService#verify}: the semantic consistency gate runs on its own
 * virtual thread and its findings are merged into the report after the build gate has finished.
 */
class ProgrammingVariantAdapterServiceVerifyTest {

    private static final long EXERCISE_ID = 7L;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private HyperionConsistencyCheckService consistencyCheckService;

    private ProgrammingVariantAdapterService adapters;

    private ProgrammingExercise exercise;

    private VariantJob job;

    @BeforeEach
    void setUp() {
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        consistencyCheckService = mock(HyperionConsistencyCheckService.class);

        adapters = new ProgrammingVariantAdapterService(mock(HyperionProgrammingExerciseContextRendererService.class), mock(ProgrammingExerciseImportService.class),
                mock(ProgrammingExerciseValidationService.class), programmingExerciseRepository, mock(ProgrammingExerciseTaskRepository.class),
                mock(ProgrammingExerciseTaskService.class), mock(ProgrammingExerciseTestCaseRepository.class), mock(UserRepository.class),
                mock(ProgrammingVariantToolsetService.class), mock(VariantBuildVerificationService.class), consistencyCheckService, mock(VariantPlacementService.class),
                mock(ExerciseVariantJobService.class), mock(ExerciseDeletionService.class));

        // No repository URIs: both build gates report a finding without any build being triggered.
        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(EXERCISE_ID);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(EXERCISE_ID)).thenReturn(exercise);

        job = mock(VariantJob.class);
        when(job.getJobId()).thenReturn("job-1");
    }

    @Test
    void shouldMergeConsistencyFindingsIntoTheReportAfterTheBuildGate() {
        ConsistencyIssueDTO issue = new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_RETURN_TYPE_MISMATCH, "Return type differs", "Fix the return type",
                List.of());
        when(consistencyCheckService.checkConsistency(EXERCISE_ID)).thenReturn(new ConsistencyCheckResponseDTO(Instant.now(), List.of(issue), null, null, null));

        VerificationReport report = adapters.verify(exercise, null, job, mock(VariantToolset.class));

        assertThat(report.passed()).isFalse();
        assertThat(report.findings()).extracting(VerificationReport.VerificationFinding::gate).contains(VerificationReport.VerificationGate.SOLUTION_BUILD,
                VerificationReport.VerificationGate.TEMPLATE_BUILD, VerificationReport.VerificationGate.CONSISTENCY);
        assertThat(report.findings()).anySatisfy(finding -> {
            assertThat(finding.gate()).isEqualTo(VerificationReport.VerificationGate.CONSISTENCY);
            assertThat(finding.message()).contains("Return type differs").contains("Suggested fix: Fix the return type");
        });
        verify(consistencyCheckService).checkConsistency(EXERCISE_ID);
    }

    @Test
    void shouldSkipTheSemanticGateWhenTheCheckerFails() {
        when(consistencyCheckService.checkConsistency(EXERCISE_ID)).thenThrow(new IllegalStateException("checker down"));

        VerificationReport report = adapters.verify(exercise, null, job, mock(VariantToolset.class));

        assertThat(report.findings()).extracting(VerificationReport.VerificationFinding::gate).doesNotContain(VerificationReport.VerificationGate.CONSISTENCY);
    }
}
