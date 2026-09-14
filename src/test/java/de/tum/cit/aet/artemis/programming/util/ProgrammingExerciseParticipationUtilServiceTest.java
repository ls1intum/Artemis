package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;

/**
 * Looking a participation up by a student's login is something only tests do, so the lookup lives in
 * {@link ProgrammingExerciseParticipationUtilService} rather than in the production service. These tests came with it:
 * the routing is not obvious, because in team mode the repository belongs to the team rather than to the student, and
 * getting it wrong hands one student another's repository.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseParticipationUtilServiceTest {

    private static final long EXERCISE_ID = 7L;

    private static final String LOGIN = "ge12abc";

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationTestRepository;

    @Mock
    private TeamRepository teamRepositoryForParticipationLookup;

    @Mock
    private UserTestRepository userRepositoryForParticipationLookup;

    @InjectMocks
    private ProgrammingExerciseParticipationUtilService utilService;

    private ProgrammingExercise exercise;

    private User student;

    @BeforeEach
    void setUp() {
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        student = new User();
        student.setId(11L);
        student.setLogin(LOGIN);
    }

    @Test
    void findStudentParticipation_forAnIndividualExercise_looksTheStudentUpByTheirLogin() {
        var studentParticipation = new ProgrammingExerciseStudentParticipation();
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        when(programmingExerciseStudentParticipationTestRepository.findByExerciseIdAndStudentLogin(EXERCISE_ID, LOGIN)).thenReturn(Optional.of(studentParticipation));

        assertThat(utilService.findStudentParticipationByExerciseAndStudentLogin(exercise, LOGIN)).isSameAs(studentParticipation);
    }

    @Test
    void findStudentParticipation_forATeamExercise_looksUpTheTeamTheStudentBelongsTo() {
        // In team mode the repository belongs to the team, not to the student; looking it up by login would find nothing at all.
        var team = new Team();
        team.setId(4L);
        var teamParticipation = new ProgrammingExerciseStudentParticipation();
        exercise.setMode(ExerciseMode.TEAM);
        when(userRepositoryForParticipationLookup.findOneByLogin(LOGIN)).thenReturn(Optional.of(student));
        when(teamRepositoryForParticipationLookup.findOneByExerciseIdAndUserId(EXERCISE_ID, student.getId())).thenReturn(Optional.of(team));
        when(programmingExerciseStudentParticipationTestRepository.findByExerciseIdAndTeamId(EXERCISE_ID, 4L)).thenReturn(Optional.of(teamParticipation));

        assertThat(utilService.findStudentParticipationByExerciseAndStudentLogin(exercise, LOGIN)).isSameAs(teamParticipation);
    }

    @Test
    void findStudentParticipation_forAStudentWhoIsInNoTeam_isReported() {
        exercise.setMode(ExerciseMode.TEAM);
        when(userRepositoryForParticipationLookup.findOneByLogin(LOGIN)).thenReturn(Optional.of(student));
        when(teamRepositoryForParticipationLookup.findOneByExerciseIdAndUserId(EXERCISE_ID, student.getId())).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> utilService.findStudentParticipationByExerciseAndStudentLogin(exercise, LOGIN));
    }

    @Test
    void findStudentParticipation_forAStudentWhoNeverParticipated_isReported() {
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        when(programmingExerciseStudentParticipationTestRepository.findByExerciseIdAndStudentLogin(EXERCISE_ID, LOGIN)).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> utilService.findStudentParticipationByExerciseAndStudentLogin(exercise, LOGIN));
    }

    @Test
    void findStudentParticipations_returnsEveryParticipationTheStudentHasInTheExercise() {
        // A practice run after the due date is a second participation, and an exercise reset creates further ones.
        var first = new ProgrammingExerciseStudentParticipation();
        var second = new ProgrammingExerciseStudentParticipation();
        when(programmingExerciseStudentParticipationTestRepository.findAllByExerciseIdAndStudentLogin(EXERCISE_ID, LOGIN)).thenReturn(List.of(first, second));

        assertThat(utilService.findStudentParticipationsByExerciseAndStudentLogin(exercise, LOGIN)).containsExactly(first, second);
    }
}
