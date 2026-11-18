package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.service.ExerciseEditorSyncService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.hyperion.domain.ArtifactType;
import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.dto.ArtifactLocationDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ConsistencyIssueDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@ExtendWith(MockitoExtension.class)
class GenerationReviewServiceTest {

    @Mock
    private ExerciseReviewService exerciseReviewService;

    @Mock
    private ExerciseEditorSyncService exerciseEditorSyncService;

    private GenerationReviewService reviewService;

    private ProgrammingExercise exercise;

    private User user;

    @BeforeEach
    void setUp() {
        reviewService = new GenerationReviewService(exerciseReviewService, exerciseEditorSyncService);
        exercise = new ProgrammingExercise();
        exercise.setId(42L);
        user = new User();
    }

    @Test
    void attachFindings_attachesBlockingFindingAsHighSeverityAndBroadcastsIt() {
        SpecFidelityReport report = report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
        Map<RepositoryType, String> savedHeads = Map.of(RepositoryType.TEMPLATE, "saved-template");
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any(), eq(user), eq(17L), eq(savedHeads))).thenReturn(List.of(thread()));

        int created = reviewService.attachFindings(exercise, user, report, 17L, savedHeads);

        assertThat(created).isOne();
        assertThat(GenerationReviewService.toReviewFindings(report)).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.HIGH);
            assertThat(finding.description()).contains("contradict");
        });
        verify(exerciseEditorSyncService).broadcastReviewThreadUpdate(eq(42L), any());
    }

    @Test
    void attachFindings_conceptAdmissionFindingRendersAsHighSeverityNamingTheConceptReview() {
        SpecFidelityReport report = report(SpecFidelityReport.Kind.CONCEPT_ADMISSION_FINDING);
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any(), eq(user))).thenReturn(List.of(thread()));

        int created = reviewService.attachFindings(exercise, user, report);

        assertThat(created).isOne();
        assertThat(GenerationReviewService.toReviewFindings(report)).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.HIGH);
            // Mechanism-neutral: the run reaches this finding both when no candidate was admitted and when a review asking for a different concept could not be given one. The
            // finding text states which, so the wrapper must not assert one of them.
            assertThat(finding.description()).contains("concept review did not approve the concept", "required behavior");
        });
    }

    @Test
    void attachFindings_mapsAdvisoryFindingAsMediumSeverity() {
        SpecFidelityReport report = report(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE);
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any(), eq(user))).thenReturn(List.of(thread()));

        assertThat(reviewService.attachFindings(exercise, user, report)).isOne();
        assertThat(GenerationReviewService.toReviewFindings(report)).singleElement().satisfies(finding -> assertThat(finding.severity()).isEqualTo(Severity.MEDIUM));
    }

    @Test
    void attachFindings_emptyReportCreatesNothing() {
        assertThat(reviewService.attachFindings(exercise, user, SpecFidelityReport.empty())).isZero();

        verify(exerciseReviewService, never()).createConsistencyCheckThreads(anyLong(), any(), any());
    }

    @Test
    void attachFindings_failureIsReportedWithoutThrowing() {
        when(exerciseReviewService.createConsistencyCheckThreads(anyLong(), any(), any())).thenThrow(new IllegalStateException("database unavailable"));

        int created = reviewService.attachFindings(exercise, user, report(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION));

        assertThat(created).isEqualTo(GenerationReviewService.REVIEW_COMMENTS_FAILED);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "MECHANICS_LEAK|HIGH|Grader-mechanics phrasing in the student-facing problem statement: \"required behavior\"",
            "MISSING_WORKED_EXAMPLE|MEDIUM|Important behaviour may benefit from a concrete worked example: \"required behavior\"",
            "INVENTED_REQUIREMENT|HIGH|Requirement not asked for by the brief (confirm or remove): \"required behavior\"",
            "UNREQUESTED_ADAPTATION_CHANGE|HIGH|Adaptation changed content outside the requested scope: \"required behavior\"",
            "REQUESTED_ADAPTATION_CHANGE_MISSING|HIGH|Requested adaptation change is missing or incomplete: \"required behavior\"",
            "ADAPTATION_SCOPE_REVIEW_UNAVAILABLE|HIGH|Adaptation scope could not be verified automatically",
            "UNCOVERED_REQUIREMENT|HIGH|Possible coverage gap against the brief: \"required behavior\"",
            "MISSING_FAILURE_MESSAGE|MEDIUM|Graded tests give no failure message, so a failing student sees only \"expected X but was Y\": required behavior",
            "CONTRACT_CONTRADICTION|HIGH|Generated artifacts contradict the student-facing contract: \"required behavior\"",
            "HIDDEN_GRADED_REQUIREMENT|HIGH|Graded requirement is not discoverable by students: \"required behavior\"",
            "WEAK_TEST_ORACLE|MEDIUM|An independent text review suspects the generated tests may allow an incorrect implementation (not execution-proven): \"required behavior\"",
            "EXECUTABLE_WEAK_TEST_ORACLE|HIGH|Execution proved that the generated tests allow an incorrect implementation: \"required behavior\"",
            "EXECUTABLE_ORACLE_PENDING_SPEC_APPROVAL|MEDIUM|Execution found a grading gap, but the rule still requires instructor approval before the tests are strengthened: \"required behavior\"",
            "TEMPLATE_QUALITY_GAP|HIGH|Student task and starter scaffold need alignment: \"required behavior\"",
            "UNENFORCEABLE_TECHNIQUE_RULE|HIGH|Students can pass without using the technique the exercise teaches: required behavior",
            "CONTRACT_WITNESS_AVAILABLE|MEDIUM|A ready-to-adopt test for a specification rule, already passing against the reference solution: required behavior",
            "CONTRACT_WITNESS_ADJUDICATION_UNAVAILABLE|MEDIUM|An executed optional test was not adopted because its contract/ownership review was incomplete: required behavior",
            "EXECUTABLE_EVIDENCE_UNAVAILABLE|HIGH|Executable quality evidence could not be completed automatically",
            "QUALITY_REVIEW_UNAVAILABLE|HIGH|Generated exercise quality could not be reviewed automatically",
            "SPECIFICATION_REVIEW_FINDING|HIGH|The compiled exercise specification still has an unresolved review finding: required behavior",
            "CONCEPT_ADMISSION_FINDING|HIGH|The concept review did not approve the concept this exercise was built from: required behavior" })
    void attachesEveryFindingKindWithItsInstructorFacingMeaningAndSeverity(SpecFidelityReport.Kind kind, Severity severity, String description) {
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any(), eq(user))).thenReturn(List.of(thread()));

        assertThat(reviewService.attachFindings(exercise, user, report(kind))).isOne();

        ArgumentCaptor<List<ConsistencyIssueDTO>> findings = ArgumentCaptor.captor();
        verify(exerciseReviewService).createConsistencyCheckThreads(eq(42L), findings.capture(), eq(user));
        assertThat(findings.getValue()).containsExactly(new ConsistencyIssueDTO(severity, ConsistencyIssueCategory.GENERATION_REVIEW_REQUIRED, description, "Review this behavior.",
                List.of(new ArtifactLocationDTO(ArtifactType.PROBLEM_STATEMENT, "", 1, 1))));
        verify(exerciseEditorSyncService).broadcastReviewThreadUpdate(eq(42L), any());
    }

    private static SpecFidelityReport report(SpecFidelityReport.Kind kind) {
        return new SpecFidelityReport(List.of(new SpecFidelityReport.Finding(kind, "required behavior", "Review this behavior.")));
    }

    private CommentThread thread() {
        CommentThread thread = new CommentThread();
        thread.setId(1L);
        thread.setExercise(exercise);
        thread.setTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);
        thread.setInitialLineNumber(1);
        thread.setOutdated(false);
        thread.setResolved(false);
        return thread;
    }
}
