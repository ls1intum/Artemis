package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Proves that {@code CHECK_EXERCISE_COURSE_OR_EXERCISE_GROUP} is in place and that it holds for a write that never
 * passes a REST resource.
 * <p>
 * {@link Exercise#checkCourseAndExerciseGroupExclusivity} already rejects a request naming both owners or neither, but
 * it only runs on the create, update and import endpoints. Anything saving through a repository skips it, which is how
 * such a row could be written before the constraint existed.
 */
class ExerciseOwnerConstraintIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseowner";

    @Autowired
    private ExamUtilService examUtilService;

    private Exercise exerciseNamed(Course course, ExerciseGroup exerciseGroup) {
        Exercise exercise = new TextExercise();
        exercise.setTitle(TEST_PREFIX + "-exercise");
        exercise.setCourse(course);
        exercise.setExerciseGroup(exerciseGroup);
        return exercise;
    }

    @Test
    void anExerciseNamingBothACourseAndAnExerciseGroupIsRejected() {
        Course course = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);

        assertThatExceptionOfType(DataIntegrityViolationException.class).as("an exam exercise must not also name a course of its own")
                .isThrownBy(() -> exerciseRepository.saveAndFlush(exerciseNamed(course, exerciseGroup)));
    }

    @Test
    void anExerciseNamingNeitherACourseNorAnExerciseGroupIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).as("an exercise that belongs to nothing must not be storable")
                .isThrownBy(() -> exerciseRepository.saveAndFlush(exerciseNamed(null, null)));
    }

    @Test
    void anExerciseNamingExactlyOneOwnerIsAccepted() {
        Course course = courseUtilService.addEmptyCourse();
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);

        assertThatCode(() -> exerciseRepository.saveAndFlush(exerciseNamed(course, null))).doesNotThrowAnyException();
        assertThatCode(() -> exerciseRepository.saveAndFlush(exerciseNamed(null, exerciseGroup))).doesNotThrowAnyException();
    }
}
