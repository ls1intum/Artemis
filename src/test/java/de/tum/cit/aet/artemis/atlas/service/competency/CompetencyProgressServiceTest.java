package de.tum.cit.aet.artemis.atlas.service.competency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
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
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Unit tests for the concurrency handling in {@link CompetencyProgressService#updateCompetencyProgress}.
 * <p>
 * {@code updateCompetencyProgress} does a non-atomic find-then-insert. Several {@code @Async} progress
 * updates for the SAME (competency, user) can run in parallel (e.g. two quick lecture-unit completions, or a
 * completion racing an exercise submission). Each finds no existing progress row, so each inserts one and the
 * second insert violates the {@code competency_user} primary key.
 * <p>
 * This verifies the losing thread reconciles the conflict — re-fetch the row the winner created and re-apply
 * its freshly computed progress as an UPDATE — instead of propagating the exception or silently dropping the
 * update (the previous behaviour). The recovery is DB-portable: it relies only on the unique constraint and
 * the standard {@link DataIntegrityViolationException}, not a vendor-specific upsert.
 */
@ExtendWith(MockitoExtension.class)
class CompetencyProgressServiceTest {

    private static final long COMPETENCY_ID = 42L;

    private static final long USER_ID = 101L;

    private static final long COURSE_ID = 7L;

    private static final double SENTINEL = -1.0;

    @Mock
    private CompetencyProgressRepository competencyProgressRepository;

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
    }

    @Test
    void shouldReconcileConcurrentProgressCreationWithoutDroppingTheUpdate() {
        when(courseCompetencyRepository.findById(COMPETENCY_ID)).thenReturn(Optional.of(competency));
        when(courseCompetencyRepository.findAllExerciseInfoByCompetencyIdAndUser(anyLong(), any(User.class))).thenReturn(Collections.emptySet());
        when(courseCompetencyRepository.findAllLectureUnitInfoByCompetencyIdAndUser(anyLong(), any(User.class))).thenReturn(Collections.emptySet());

        // The row created by the winning concurrent thread; the sentinel values must be overwritten by the reconcile.
        CompetencyProgress winnerRow = new CompetencyProgress();
        winnerRow.setCompetency(competency);
        winnerRow.setUser(user);
        winnerRow.setProgress(SENTINEL);
        winnerRow.setConfidence(SENTINEL);

        // Initial lookup finds nothing (both threads race here); the reconcile re-fetch finds the winner's row.
        when(competencyProgressRepository.findByCompetencyIdAndUserId(COMPETENCY_ID, USER_ID)).thenReturn(Optional.empty(), Optional.of(winnerRow));

        // Our own INSERT loses the race -> unique-constraint violation; the reconcile UPDATE then succeeds.
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"competency_user_pkey\"")).doReturn(winnerRow)
                .when(competencyProgressRepository).save(any(CompetencyProgress.class));

        assertThatCode(() -> competencyProgressService.updateCompetencyProgress(COMPETENCY_ID, user)).doesNotThrowAnyException();

        ArgumentCaptor<CompetencyProgress> saveCaptor = ArgumentCaptor.forClass(CompetencyProgress.class);
        verify(competencyProgressRepository, times(2)).save(saveCaptor.capture());
        List<CompetencyProgress> savedRows = saveCaptor.getAllValues();
        CompetencyProgress ownAttempt = savedRows.get(0); // the losing INSERT, carrying the freshly computed values
        CompetencyProgress reconciled = savedRows.get(1); // the winner's row, re-saved with our values

        // Non-lossy: the reconcile re-applied our computed progress/confidence onto the winner's row (not dropped).
        assertThat(reconciled).isSameAs(winnerRow);
        assertThat(reconciled.getProgress()).isEqualTo(ownAttempt.getProgress());
        assertThat(reconciled.getConfidence()).isEqualTo(ownAttempt.getConfidence());
        assertThat(reconciled.getProgress()).isNotEqualTo(SENTINEL);

        // Learning-path propagation still runs after a reconciled progress update.
        verify(learningPathService).updateLearningPathProgress(COURSE_ID, USER_ID);
    }
}
