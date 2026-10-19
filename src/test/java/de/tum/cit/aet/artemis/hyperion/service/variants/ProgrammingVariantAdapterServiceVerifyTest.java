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
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyCheckResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
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

    private VariantBuildVerificationService builds;

    private ProgrammingExerciseTaskService tasks;

    private ProgrammingExerciseTestCaseTestRepository testCases;

    @BeforeEach
    void setUp() {
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        consistencyCheckService = mock(HyperionConsistencyCheckService.class);

        builds = mock(VariantBuildVerificationService.class);
        tasks = mock(ProgrammingExerciseTaskService.class);
        testCases = mock(ProgrammingExerciseTestCaseTestRepository.class);
        adapters = new ProgrammingVariantAdapterService(mock(HyperionProgrammingExerciseContextRendererService.class), mock(ProgrammingExerciseImportService.class),
                mock(ProgrammingExerciseValidationService.class), programmingExerciseRepository, mock(ProgrammingExerciseTaskRepository.class), tasks, testCases,
                mock(UserRepository.class), mock(ProgrammingVariantToolsetService.class), builds, consistencyCheckService, mock(VariantPlacementService.class),
                mock(ExerciseVariantJobService.class), mock(ExerciseDeletionService.class), mock(TeamAssignmentConfigRepository.class),
                mock(PlagiarismDetectionConfigRepository.class));

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

    private void configureBuilds(VariantBuildVerificationService.BuildResultOutcome solution, VariantBuildVerificationService.BuildResultOutcome template) throws Exception {
        var uri = mock(de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri.class);
        when(exercise.getRepositoryURI(org.mockito.ArgumentMatchers.any())).thenReturn(uri);
        when(builds.triggerBuild(org.mockito.ArgumentMatchers.eq(exercise), org.mockito.ArgumentMatchers.eq(uri), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new VariantBuildVerificationService.PendingBuild("hash", Instant.now()));
        when(builds.waitForBuildResults(org.mockito.ArgumentMatchers.eq(exercise), org.mockito.ArgumentMatchers.anyMap())).thenReturn(java.util.Map
                .of(de.tum.cit.aet.artemis.programming.domain.RepositoryType.SOLUTION, solution, de.tum.cit.aet.artemis.programming.domain.RepositoryType.TEMPLATE, template));
        when(programmingExerciseRepository.findByIdElseThrow(Long.valueOf(EXERCISE_ID))).thenReturn(exercise);
        when(tasks.findUnresolvedTaskTestReferences(exercise)).thenReturn(List.of());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(VariantBuildVerificationService.BuildResultState.class)
    void buildOutcomeStatesProduceTheirOwnGateFindings(VariantBuildVerificationService.BuildResultState state) throws Exception {
        var outcome = new VariantBuildVerificationService.BuildResultOutcome(null, state);
        configureBuilds(outcome, outcome);
        when(builds.describeBuildResult(null)).thenReturn("no build result");
        when(builds.unexpectedlyPassingTestNames(null)).thenReturn(List.of());
        VerificationReport report = adapters.verify(exercise, null, job, mock(VariantToolset.class));
        if (state == VariantBuildVerificationService.BuildResultState.SUCCESS) {
            assertThat(report.passed()).isTrue();
            assertThat(report.findings()).isEmpty();
        }
        else {
            assertThat(report.passed()).isFalse();
            assertThat(report.findings()).hasSize(2);
            assertThat(report.findings()).extracting(VerificationReport.VerificationFinding::gate).containsExactlyInAnyOrder(VerificationReport.VerificationGate.SOLUTION_BUILD,
                    VerificationReport.VerificationGate.TEMPLATE_BUILD);
            assertThat(report.findings()).allSatisfy(finding -> assertThat(finding.message()).contains(switch (state) {
                case FAILED -> "Current result: no build result";
                case TIMED_OUT -> "BuildResultState.TIMED_OUT";
                default -> state.name();
            }));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = { 0, 1, -1 })
    void templateFailureIdentifiesZeroTestsOrUnexpectedPassingTests(int count) throws Exception {
        var result = mock(de.tum.cit.aet.artemis.assessment.domain.Result.class);
        when(result.getTestCaseCount()).thenReturn(count == -1 ? null : count);
        configureBuilds(new VariantBuildVerificationService.BuildResultOutcome(null, VariantBuildVerificationService.BuildResultState.SUCCESS),
                new VariantBuildVerificationService.BuildResultOutcome(result, VariantBuildVerificationService.BuildResultState.FAILED));
        when(builds.describeBuildResult(result)).thenReturn("compiler logs");
        when(builds.unexpectedlyPassingTestNames(result)).thenReturn(List.of("shouldBeMissing"));
        var report = adapters.verify(exercise, null, job, mock(VariantToolset.class));
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.gate()).isEqualTo(VerificationReport.VerificationGate.TEMPLATE_BUILD);
            assertThat(finding.message()).contains("compiler logs");
            assertThat(finding.message()).contains(count == 0 ? "executed NO tests at all" : "shouldBeMissing");
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "typo", "<testid>namedTest</testid>" })
    void successfulBuildsValidatePersistedTaskReferencesAndListExactNames(String unresolved) throws Exception {
        var success = new VariantBuildVerificationService.BuildResultOutcome(null, VariantBuildVerificationService.BuildResultState.SUCCESS);
        configureBuilds(success, success);
        when(tasks.findUnresolvedTaskTestReferences(exercise)).thenReturn(List.of(unresolved));
        var test = new de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase();
        test.setTestName("testClass[StudentClass]");
        when(testCases.findByExerciseId(EXERCISE_ID)).thenReturn(java.util.Set.of(test));
        var report = adapters.verify(exercise, null, job, mock(VariantToolset.class));
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.gate()).isEqualTo(VerificationReport.VerificationGate.TEST_REFERENCES);
            assertThat(finding.message()).contains(unresolved, "[testClass[StudentClass]]");
            assertThat(finding.message()).contains(unresolved.startsWith("<testid>") ? "may ONLY contain a numeric test-case id" : "use the exact current test name");
        });
    }

    @Test
    void consistencySkipsNullIssuesAndEmptySuggestedFixes() {
        when(consistencyCheckService.checkConsistency(EXERCISE_ID)).thenReturn(new ConsistencyCheckResponseDTO(Instant.now(),
                java.util.Arrays.asList(null, new ConsistencyIssueDTO(Severity.HIGH, ConsistencyIssueCategory.METHOD_RETURN_TYPE_MISMATCH, "Mismatch", " ", List.of())), null, null,
                null));
        assertThat(adapters.verify(exercise, null, job, mock(VariantToolset.class)).findings())
                .filteredOn(finding -> finding.gate() == VerificationReport.VerificationGate.CONSISTENCY).singleElement()
                .satisfies(finding -> assertThat(finding.message()).contains("Mismatch").doesNotContain("Suggested fix:"));
    }

}
