package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unit test of the course guard used when the last (rated) result attributes of a participant score are updated.
 */
class ParticipantScoreScheduleServiceCourseGuardTest {

    private ParticipantScoreScheduleService service;

    private TextExercise exercise;

    private Result result;

    @BeforeEach
    void setUp() {
        service = new ParticipantScoreScheduleService(mock(TaskScheduler.class), Optional.empty(), mock(ParticipantScoreRepository.class), mock(StudentScoreRepository.class),
                mock(TeamScoreRepository.class), mock(ExerciseRepository.class), mock(ResultTestRepository.class), mock(UserRepository.class), mock(TeamRepository.class));
        exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setMaxPoints(10.0);
        result = new Result();
        result.setScore(50.0);
    }

    @Test
    void setLastAttributes_exerciseWithoutCourse_throws() {
        var score = new StudentScore();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "setLastAttributes", score, result, exercise)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The course of exercise 42 cannot be resolved");
    }

    @Test
    void setLastRatedAttributes_exerciseWithoutCourse_throws() {
        var score = new StudentScore();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "setLastRatedAttributes", score, result, exercise)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The course of exercise 42 cannot be resolved");
    }

    @Test
    void setLastAttributes_exerciseWithCourse_setsRoundedPoints() {
        var course = new Course();
        course.setId(1L);
        exercise.setCourse(course);
        var score = new StudentScore();

        ReflectionTestUtils.invokeMethod(service, "setLastAttributes", score, result, exercise);

        assertThat(score.getLastScore()).isEqualTo(50.0);
        assertThat(score.getLastPoints()).isEqualTo(5.0);
    }

    @Test
    void setLastRatedAttributes_exerciseWithCourse_setsRoundedPoints() {
        var course = new Course();
        course.setId(1L);
        exercise.setCourse(course);
        var score = new StudentScore();

        ReflectionTestUtils.invokeMethod(service, "setLastRatedAttributes", score, result, exercise);

        assertThat(score.getLastRatedScore()).isEqualTo(50.0);
        assertThat(score.getLastRatedPoints()).isEqualTo(5.0);
    }

    @Test
    void missingMaximumPoints_keepsScoresWithoutInventingPoints() {
        exercise.setMaxPoints(null);
        var score = new StudentScore();
        score.setLastPoints(7.0);
        score.setLastRatedPoints(7.0);

        ReflectionTestUtils.invokeMethod(service, "setLastAttributes", score, result, exercise);
        ReflectionTestUtils.invokeMethod(service, "setLastRatedAttributes", score, result, exercise);

        assertThat(score.getLastScore()).isEqualTo(50.0);
        assertThat(score.getLastRatedScore()).isEqualTo(50.0);
        assertThat(score.getLastPoints()).isNull();
        assertThat(score.getLastRatedPoints()).isNull();
    }

    @Test
    void missingScore_keepsPointsUnavailable() {
        result.score(null);
        var score = new StudentScore();

        ReflectionTestUtils.invokeMethod(service, "setLastAttributes", score, result, exercise);
        ReflectionTestUtils.invokeMethod(service, "setLastRatedAttributes", score, result, exercise);

        assertThat(score.getLastPoints()).isNull();
        assertThat(score.getLastRatedPoints()).isNull();
    }

}
