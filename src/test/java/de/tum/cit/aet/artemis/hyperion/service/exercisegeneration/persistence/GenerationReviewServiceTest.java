package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadLocationType;
import de.tum.cit.aet.artemis.exercise.service.ExerciseEditorSyncService;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

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
        when(exerciseReviewService.createConsistencyCheckThreads(eq(42L), any(), eq(user))).thenReturn(List.of(thread()));

        int created = reviewService.attachFindings(exercise, user, report);

        assertThat(created).isOne();
        assertThat(GenerationReviewService.toReviewFindings(report)).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.HIGH);
            assertThat(finding.description()).contains("contradict");
        });
        verify(exerciseEditorSyncService).broadcastReviewThreadUpdate(eq(42L), any());
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
