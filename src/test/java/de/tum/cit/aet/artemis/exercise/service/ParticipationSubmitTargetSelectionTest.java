package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.dto.StudentParticipationSubmitTargetDTO;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ParticipationSubmitTargetSelectionTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-01-01T10:00:00Z");

    private final StudentParticipationTestRepository participations = mock(StudentParticipationTestRepository.class);

    private final TeamRepository teams = mock(TeamRepository.class);

    private final ParticipationService service = new ParticipationService(Optional.empty(), Optional.empty(), null, participations, null, null, null, teams, null, null, null, null,
            null, null, Optional.empty());

    private final TextExercise exercise = new TextExercise();

    private final User student = new User();

    @AfterEach
    void noOtherRepositoryLookups() {
        verifyNoMoreInteractions(teams, participations);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void nullIndividualDeadlineFallsBackToExerciseDeadlineAndPrefersPractice(boolean teamMode) {
        var graded = target(1, null, false);
        var practice = target(2, null, true);
        stubGraded(teamMode, Optional.of(graded));
        exercise.setDueDate(NOW.minusDays(1));
        when(participations.findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true)).thenReturn(Optional.of(practice));
        assertSelection(practice);
        var order = verifyGradedLookup(teamMode);
        order.verify(participations).findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true);
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void individualExtensionPreventsPracticeLookupAfterExerciseDeadline(boolean teamMode) {
        var graded = target(1, NOW.plusDays(1), false);
        stubGraded(teamMode, Optional.of(graded));
        exercise.setDueDate(NOW.minusDays(1));
        assertSelection(graded);
        verifyGradedLookup(teamMode).verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void exactDeadlineDoesNotSelectPractice(boolean teamMode) {
        var graded = target(1, NOW, false);
        stubGraded(teamMode, Optional.of(graded));
        exercise.setDueDate(NOW.minusDays(1));
        assertSelection(graded);
        verifyGradedLookup(teamMode).verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void missingPracticeKeepsGradedTargetAfterIndividualDeadline(boolean teamMode) {
        var graded = target(1, NOW.minusDays(1), false);
        stubGraded(teamMode, Optional.of(graded));
        exercise.setDueDate(NOW.plusDays(1));
        when(participations.findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true)).thenReturn(Optional.empty());
        assertSelection(graded);
        var order = verifyGradedLookup(teamMode);
        order.verify(participations).findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true);
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void noDeadlineMakesNoPracticeQueryEvenWithoutGradedTarget(boolean teamMode) {
        stubGraded(teamMode, Optional.empty());
        assertSelection(null);
        verifyGradedLookup(teamMode).verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void practiceCanBeSelectedWithoutGradedTarget(boolean teamMode) {
        stubGraded(teamMode, Optional.empty());
        exercise.setDueDate(NOW.minusDays(1));
        var practice = target(2, null, true);
        when(participations.findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true)).thenReturn(Optional.of(practice));
        assertSelection(practice);
        var order = verifyGradedLookup(teamMode);
        order.verify(participations).findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true);
        order.verifyNoMoreInteractions();
    }

    @Test
    void regularExamFallsBackToLatestOnlyAfterEmptyPracticeLookup() {
        stubGraded(false, Optional.empty());
        exam(false);
        exercise.setDueDate(NOW.minusDays(1));
        var latest = target(3, null, true);
        when(participations.findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true)).thenReturn(Optional.empty());
        when(participations.findLatestSubmitTargetByExerciseIdAndStudentId(10L, 20L)).thenReturn(Optional.of(latest));
        assertSelection(latest);
        var order = verifyGradedLookup(false);
        order.verify(participations).findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true);
        order.verify(participations).findLatestSubmitTargetByExerciseIdAndStudentId(10L, 20L);
        order.verifyNoMoreInteractions();
    }

    @Test
    void regularExamDoesNotFetchLatestWhenPracticeExists() {
        stubGraded(false, Optional.empty());
        exam(false);
        exercise.setDueDate(NOW.minusDays(1));
        var practice = target(2, null, true);
        when(participations.findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true)).thenReturn(Optional.of(practice));
        assertSelection(practice);
        var order = verifyGradedLookup(false);
        order.verify(participations).findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true);
        order.verifyNoMoreInteractions();
    }

    @Test
    void testExamGoesDirectlyToLatestTarget() {
        exercise.setId(10L);
        student.setId(20L);
        exam(true);
        exercise.setDueDate(NOW.minusDays(1));
        var latest = target(3, null, true);
        when(participations.findLatestSubmitTargetByExerciseIdAndStudentId(10L, 20L)).thenReturn(Optional.of(latest));
        assertSelection(latest);
        var order = inOrder(teams, participations);
        order.verify(participations).findLatestSubmitTargetByExerciseIdAndStudentId(10L, 20L);
        order.verifyNoMoreInteractions();
    }

    @Test
    void missingTeamStillAllowsTheStudentsOwnPracticeTarget() {
        exercise.setId(10L);
        student.setId(20L);
        exercise.setMode(ExerciseMode.TEAM);
        exercise.setDueDate(NOW.minusDays(1));
        when(teams.findOneByExerciseIdAndUserId(10L, 20L)).thenReturn(Optional.empty());
        var practice = target(2, null, true);
        when(participations.findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true)).thenReturn(Optional.of(practice));
        assertSelection(practice);
        var order = inOrder(teams, participations);
        order.verify(teams).findOneByExerciseIdAndUserId(10L, 20L);
        order.verify(participations).findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, true);
        order.verifyNoMoreInteractions();
    }

    private void stubGraded(boolean teamMode, Optional<StudentParticipationSubmitTargetDTO> graded) {
        exercise.setId(10L);
        student.setId(20L);
        exercise.setMode(teamMode ? ExerciseMode.TEAM : ExerciseMode.INDIVIDUAL);
        if (teamMode) {
            var team = new Team();
            team.setId(30L);
            when(teams.findOneByExerciseIdAndUserId(10L, 20L)).thenReturn(Optional.of(team));
            when(participations.findSubmitTargetByExerciseIdAndTeamId(10L, 30L)).thenReturn(graded);
        }
        else {
            when(participations.findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, false)).thenReturn(graded);
        }
    }

    private InOrder verifyGradedLookup(boolean teamMode) {
        var order = inOrder(teams, participations);
        if (teamMode) {
            order.verify(teams).findOneByExerciseIdAndUserId(10L, 20L);
            order.verify(participations).findSubmitTargetByExerciseIdAndTeamId(10L, 30L);
        }
        else {
            order.verify(participations).findSubmitTargetByExerciseIdAndStudentIdAndTestRun(10L, 20L, false);
        }
        return order;
    }

    private void assertSelection(StudentParticipationSubmitTargetDTO expected) {
        try (var clock = mockStatic(ZonedDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(ZonedDateTime::now).thenReturn(NOW);
            var result = service.findSubmitTargetByExerciseAndStudent(exercise, student);
            assertThat(result).isEqualTo(Optional.ofNullable(expected));
        }
    }

    private void exam(boolean testExam) {
        var exam = new Exam();
        exam.setTestExam(testExam);
        var group = new ExerciseGroup();
        group.setExam(exam);
        exercise.setExerciseGroup(group);
    }

    private static StudentParticipationSubmitTargetDTO target(long id, ZonedDateTime individualDueDate, boolean practice) {
        return new StudentParticipationSubmitTargetDTO(id, InitializationState.INITIALIZED, null, individualDueDate, practice, null);
    }
}
