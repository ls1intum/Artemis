package de.tum.cit.aet.artemis.exercise.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseCourseResolutionTest {

    @Test
    void courseExercise_resolvesItsCourse() {
        var course = new Course();
        var exercise = new TextExercise();
        exercise.setCourse(course);

        assertThat(exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow()).isSameAs(course);
    }

    @Test
    void examExercise_resolvesTheCourseOfItsExam() {
        var course = new Course();
        var exam = new Exam();
        exam.setCourse(course);
        var exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(exam);
        var exercise = new TextExercise();
        exercise.setExerciseGroup(exerciseGroup);

        assertThat(exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow()).isSameAs(course);
    }

    @Test
    void examExerciseWithMaskedExam_throws() {
        var exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(null);
        var exercise = new TextExercise();
        exercise.setId(42L);
        exercise.setExerciseGroup(exerciseGroup);

        assertThatThrownBy(exercise::getCourseViaExerciseGroupOrCourseMemberElseThrow).isInstanceOf(IllegalStateException.class).hasMessageContaining("exercise 42")
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    void exerciseWithoutCourseAndExamGroup_throws() {
        var exercise = new TextExercise();
        exercise.setId(7L);

        assertThatThrownBy(exercise::getCourseViaExerciseGroupOrCourseMemberElseThrow).isInstanceOf(IllegalStateException.class).hasMessageContaining("exercise 7");
    }
}
