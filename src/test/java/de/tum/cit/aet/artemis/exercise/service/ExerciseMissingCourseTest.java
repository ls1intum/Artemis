package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Guards for exercises whose course cannot be resolved (for example an exam exercise whose exam is masked out).
 */
class ExerciseMissingCourseTest {

    private static TextExercise exerciseWithoutCourse() {
        var exercise = new TextExercise();
        exercise.setId(5L);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        return exercise;
    }

    @Test
    void exerciseScoresChartThrowsWhenCourseCannotBeResolved() {
        var service = new ExerciseScoresChartService(mock(StudentScoreRepository.class), mock(TeamScoreRepository.class), mock(ParticipantScoreRepository.class));
        var exercise = exerciseWithoutCourse();

        assertThatThrownBy(() -> service.getExerciseScores(Set.of(exercise), new User())).isInstanceOf(IllegalStateException.class).hasMessageContaining("5");
    }

    @Test
    void updatePointsThrowsWhenCourseCannotBeResolved() {
        var exerciseService = mock(ExerciseService.class, CALLS_REAL_METHODS);
        var participantScoreRepository = mock(ParticipantScoreRepository.class);
        ReflectionTestUtils.setField(exerciseService, "participantScoreRepository", participantScoreRepository);

        var exercise = exerciseWithoutCourse();
        exercise.setMaxPoints(10.0);
        when(participantScoreRepository.findAllByExercise(exercise)).thenReturn(List.of(mock(ParticipantScore.class)));

        assertThatThrownBy(() -> exerciseService.updatePointsInRelatedParticipantScores(5.0, null, exercise)).isInstanceOf(IllegalStateException.class).hasMessageContaining("5");
    }

    @Test
    void participationAccessCheckThrowsWhenCourseCannotBeResolved() {
        var authCheckService = mock(AuthorizationCheckService.class);
        var service = new ParticipationAuthorizationCheckService(null, null, authCheckService, null, null, null, null, Optional.empty(),
                mock(StudentParticipationRepository.class));
        var participation = mock(StudentParticipation.class);
        when(participation.getExercise()).thenReturn(exerciseWithoutCourse());
        when(participation.getId()).thenReturn(9L);
        when(authCheckService.isOwnerOfParticipation(participation)).thenReturn(false);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "userHasPermissionsToAccessParticipation", participation, new User()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("5");
    }
}
