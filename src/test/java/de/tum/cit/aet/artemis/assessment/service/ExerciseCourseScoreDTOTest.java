package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseCourseScoreDTOTest {

    @Test
    void from_exerciseWithoutCourse_throws() {
        var exercise = new TextExercise();
        exercise.setId(17L);
        exercise.setMaxPoints(10.0);

        assertThatThrownBy(() -> ExerciseCourseScoreDTO.from(exercise)).isInstanceOf(IllegalStateException.class).hasMessage("The course of exercise 17 cannot be resolved");
    }

    @Test
    void from_exerciseWithCourse_mapsFields() {
        var course = new Course();
        course.setId(5L);
        var exercise = new TextExercise();
        exercise.setId(17L);
        exercise.setMaxPoints(10.0);
        exercise.setBonusPoints(2.0);
        exercise.setCourse(course);

        var dto = ExerciseCourseScoreDTO.from(exercise);

        assertThat(dto.id()).isEqualTo(17L);
        assertThat(dto.type()).isEqualTo(ExerciseType.TEXT);
        assertThat(dto.courseId()).isEqualTo(5L);
        assertThat(dto.maxPoints()).isEqualTo(10.0);
        assertThat(dto.bonusPoints()).isEqualTo(2.0);
        assertThat(dto.variantGroupId()).isNull();
        assertThat(dto.variantGroupMaxPoints()).isNull();
    }
}
