package de.tum.cit.aet.artemis.atlas.service.competency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyProgressTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Unit tests for the concurrency handling in {@link CompetencyProgressService#updateCompetencyProgress}.
 * <p>
 * {@code updateCompetencyProgress} does a non-atomic find-then-insert. Several {@code @Async} progress updates
 * for the SAME (competency, user) can run in parallel (e.g. two quick lecture-unit completions, or a completion
 * racing an exercise submission). Each finds no existing progress row, so each inserts one and the second insert
 * violates the {@code competency_user} primary key.
 * <p>
 * On that conflict the losing thread reconciles via a single idempotent UPDATE
 * ({@link CompetencyProgressTestRepository#updateProgressAndConfidence}) rather than swallowing the exception. These
 * tests verify it does not propagate the exception, re-applies (does not drop) the freshly computed progress, and
 * — crucially — does not resurrect a row that was concurrently deleted (zero rows updated → skip, no learning-path
 * propagation).
 */
@ExtendWith(MockitoExtension.class)
class CompetencyProgressServiceTest {

    private static final long COMPETENCY_ID = 42L;

    private static final long USER_ID = 101L;

    private static final long COURSE_ID = 7L;

    @Mock
    private CompetencyProgressTestRepository competencyProgressRepository;

    @Mock
    private LearningPathService learningPathService;

    @Mock
    private ParticipantScoreService participantScoreService;

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    private CompetencyProgressService competencyProgressService;

    private Competency competency;

    private User user;

    @BeforeEach
    void setUp() {
        competencyProgressService = new CompetencyProgressService(competencyProgressRepository, learningPathService, participantScoreService,
                Optional.<LectureUnitRepositoryApi>empty(), courseCompetencyRepository);

        Course course = new Course();
        course.setId(COURSE_ID);
        competency = new Competency();
        competency.setId(COMPETENCY_ID);
        competency.setCourse(course);

        user = new User();
        user.setId(USER_ID);
        user.setLogin("student1");

        // Arrange the create path: the competency exists, there is no activity yet, and no existing progress row
        // (both racing threads reach this state), so updateCompetencyProgress attempts an INSERT.
        when(courseCompetencyRepository.findById(COMPETENCY_ID)).thenReturn(Optional.of(competency));
        when(courseCompetencyRepository.findAllExerciseInfoByCompetencyIdAndUser(anyLong(), any(User.class))).thenReturn(Collections.emptySet());
        when(courseCompetencyRepository.findAllLectureUnitInfoByCompetencyIdAndUser(anyLong(), any(User.class))).thenReturn(Collections.emptySet());
        when(competencyProgressRepository.findByCompetencyIdAndUserId(COMPETENCY_ID, USER_ID)).thenReturn(Optional.empty());
        // Our own INSERT loses the race with a concurrent creator -> unique-constraint violation.
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"competency_user_pkey\"")).when(competencyProgressRepository)
                .save(any(CompetencyProgress.class));
    }

    @Test
    void shouldReconcileConcurrentProgressCreationWithoutDroppingTheUpdate() {
        // The reconcile UPDATE finds the row the winning thread created and updates it.
        when(competencyProgressRepository.updateProgressAndConfidence(eq(COMPETENCY_ID), eq(USER_ID), any(), any(), any(), any())).thenReturn(1);

        assertThatCode(() -> competencyProgressService.updateCompetencyProgress(COMPETENCY_ID, user)).doesNotThrowAnyException();

        // Non-lossy: the values from the failed INSERT attempt are re-applied via the reconcile UPDATE.
        ArgumentCaptor<CompetencyProgress> saveCaptor = ArgumentCaptor.forClass(CompetencyProgress.class);
        verify(competencyProgressRepository).save(saveCaptor.capture());
        CompetencyProgress ownAttempt = saveCaptor.getValue();

        ArgumentCaptor<Double> progressCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> confidenceCaptor = ArgumentCaptor.forClass(Double.class);
        verify(competencyProgressRepository).updateProgressAndConfidence(eq(COMPETENCY_ID), eq(USER_ID), progressCaptor.capture(), confidenceCaptor.capture(), any(), any());
        assertThat(progressCaptor.getValue()).isEqualTo(ownAttempt.getProgress());
        assertThat(confidenceCaptor.getValue()).isEqualTo(ownAttempt.getConfidence());

        // The conflict path recomputes from the DB before writing (once for the initial attempt, once for the
        // reconcile), so it does not re-apply this thread's potentially-stale pre-race values.
        verify(courseCompetencyRepository, times(2)).findAllExerciseInfoByCompetencyIdAndUser(anyLong(), any(User.class));
        verify(courseCompetencyRepository, times(2)).findAllLectureUnitInfoByCompetencyIdAndUser(anyLong(), any(User.class));

        // Learning-path propagation still runs after a reconciled progress update.
        verify(learningPathService).updateLearningPathProgress(COURSE_ID, USER_ID);
    }

    @Test
    void shouldSkipPersistenceWhenTheRowVanishedDuringReconciliation() {
        // The row the winner created was deleted again before the reconcile UPDATE -> zero rows affected.
        when(competencyProgressRepository.updateProgressAndConfidence(eq(COMPETENCY_ID), eq(USER_ID), any(), any(), any(), any())).thenReturn(0);

        assertThatCode(() -> competencyProgressService.updateCompetencyProgress(COMPETENCY_ID, user)).doesNotThrowAnyException();

        // No resurrection (a targeted UPDATE, not a merge/insert), and no learning-path propagation for a row that
        // no longer exists.
        verify(competencyProgressRepository).updateProgressAndConfidence(eq(COMPETENCY_ID), eq(USER_ID), any(), any(), any(), any());
        verify(learningPathService, never()).updateLearningPathProgress(anyLong(), anyLong());
    }
}
