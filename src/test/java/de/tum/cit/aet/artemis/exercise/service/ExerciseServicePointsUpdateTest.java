package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class ExerciseServicePointsUpdateTest {

    @Mock
    private ParticipantScoreRepository participantScoreRepository;

    @InjectMocks
    private ExerciseService exerciseService;

    private TextExercise exerciseWithoutCourse() {
        var exercise = new TextExercise();
        exercise.setId(5L);
        exercise.setMaxPoints(20.0);
        return exercise;
    }

    @Test
    void updatePointsInRelatedParticipantScores_noScores_returnsWithoutResolvingCourse() {
        var exercise = exerciseWithoutCourse();
        when(participantScoreRepository.findAllByExercise(exercise)).thenReturn(List.of());

        assertThatCode(() -> exerciseService.updatePointsInRelatedParticipantScores(10.0, null, exercise)).doesNotThrowAnyException();

        verify(participantScoreRepository).findAllByExercise(exercise);
        verifyNoMoreInteractions(participantScoreRepository);
    }

    @Test
    void updatePointsInRelatedParticipantScores_scoresButNoCourse_throws() {
        var exercise = exerciseWithoutCourse();
        var score = new StudentScore();
        score.setLastScore(50.0);
        when(participantScoreRepository.findAllByExercise(exercise)).thenReturn(List.of(score));

        assertThatThrownBy(() -> exerciseService.updatePointsInRelatedParticipantScores(10.0, null, exercise)).isInstanceOf(IllegalStateException.class).hasMessageContaining("5");
    }

    @Test
    void updatePointsInRelatedParticipantScores_unchangedPoints_doesNothing() {
        var exercise = exerciseWithoutCourse();
        exercise.setBonusPoints(null);

        assertThatCode(() -> exerciseService.updatePointsInRelatedParticipantScores(20.0, null, exercise)).doesNotThrowAnyException();

        verifyNoMoreInteractions(participantScoreRepository);
    }

    @Test
    void updatePointsInRelatedParticipantScores_withCourse_recomputesRoundedPointsAndSaves() {
        var course = new Course();
        course.setAccuracyOfScores(1);
        var exercise = exerciseWithoutCourse();
        exercise.setCourse(course);
        var bothScores = new StudentScore();
        bothScores.setLastScore(33.333);
        bothScores.setLastRatedScore(50.0);
        var noScores = new StudentScore();
        noScores.setLastPoints(1.0);
        noScores.setLastRatedPoints(1.0);
        when(participantScoreRepository.findAllByExercise(exercise)).thenReturn(List.of(bothScores, noScores));

        exerciseService.updatePointsInRelatedParticipantScores(10.0, null, exercise);

        assertThat(bothScores.getLastPoints()).isEqualTo(6.7);
        assertThat(bothScores.getLastRatedPoints()).isEqualTo(10.0);
        assertThat(noScores.getLastPoints()).isNull();
        assertThat(noScores.getLastRatedPoints()).isNull();
        verify(participantScoreRepository).saveAll(List.of(bothScores, noScores));
    }
}
