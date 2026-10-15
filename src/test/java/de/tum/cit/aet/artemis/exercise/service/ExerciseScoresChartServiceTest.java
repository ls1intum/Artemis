package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseScoresAggregatedInformation;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class ExerciseScoresChartServiceTest {

    @Mock
    private StudentScoreRepository studentScoreRepository;

    @Mock
    private TeamScoreRepository teamScoreRepository;

    @Mock
    private ParticipantScoreRepository participantScoreRepository;

    @InjectMocks
    private ExerciseScoresChartService exerciseScoresChartService;

    @Test
    void getExerciseScores_exerciseWithoutCourse_throws() {
        var exercise = new TextExercise();
        exercise.setId(7L);

        assertThatThrownBy(() -> exerciseScoresChartService.getExerciseScores(Set.of(exercise), new User())).isInstanceOf(IllegalStateException.class).hasMessageContaining("7");
    }

    private TextExercise individualExerciseInCourse() {
        var course = new Course();
        course.setAccuracyOfScores(1);
        var exercise = new TextExercise();
        exercise.setId(7L);
        exercise.setTitle("Exercise");
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        exercise.setCourse(course);
        return exercise;
    }

    @Test
    void getExerciseScores_roundsScoresAccordingToCourseSettings() {
        var exercise = individualExerciseInCourse();
        var user = new User();
        var studentScore = new StudentScore();
        studentScore.setExercise(exercise);
        studentScore.setLastRatedScore(61.26);
        when(studentScoreRepository.findAllByExerciseAndUserWithEagerExercise(Set.of(exercise), user)).thenReturn(List.of(studentScore));
        when(participantScoreRepository.getAggregatedExerciseScoresInformation(Set.of(exercise)))
                .thenReturn(List.of(new ExerciseScoresAggregatedInformation(exercise.getId(), 40.04, 99.96)));

        var result = exerciseScoresChartService.getExerciseScores(Set.of(exercise), user);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().scoreOfStudent()).isEqualTo(61.3);
        assertThat(result.getFirst().averageScoreAchieved()).isEqualTo(40.0);
        assertThat(result.getFirst().maxScoreAchieved()).isEqualTo(100.0);
    }

    @Test
    void getExerciseScores_withoutAggregatedInformationOrScore_returnsZeros() {
        var exercise = individualExerciseInCourse();

        var result = exerciseScoresChartService.getExerciseScores(Set.of(exercise), new User());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().scoreOfStudent()).isZero();
        assertThat(result.getFirst().averageScoreAchieved()).isZero();
        assertThat(result.getFirst().maxScoreAchieved()).isZero();
    }
}
