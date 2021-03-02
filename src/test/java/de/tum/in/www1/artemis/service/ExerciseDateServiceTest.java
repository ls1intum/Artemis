package de.tum.in.www1.artemis.service;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.exam.ExamService;

class ExerciseDateServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExerciseDateService exerciseDateService;

    @Autowired
    ExamService examService;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testCourseExerciseBeforeStartHasEnded() {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        final Exercise exercise = course.getExercises().iterator().next();
        exercise.setReleaseDate(now().plusHours(1));
        exerciseRepository.save(exercise);

        final boolean actual = exerciseDateService.hasEnded(exercise);

        assertThat(actual).isFalse();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testCourseExerciseActiveHasEnded() {
        final Course course = database.addCourseWithOneReleasedTextExercise();
        final Exercise exercise = course.getExercises().iterator().next();

        final boolean actual = exerciseDateService.hasEnded(exercise);

        assertThat(actual).isFalse();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testCourseExerciseAfterDueHasEnded() {
        final Course course = database.addCourseWithOneFinishedTextExercise();
        final Exercise exercise = course.getExercises().iterator().next();

        final boolean actual = exerciseDateService.hasEnded(exercise);

        assertThat(actual).isTrue();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testExamExerciseBeforeStartHasEnded() {
        final TextExercise exercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        final Exam exam = exercise.getExerciseGroup().getExam();
        exam.setStartDate(now().plusDays(1));
        exam.setEndDate(now().plusDays(1).plusHours(1));
        examRepository.save(exam);
        database.addStudentExam(exam);

        final boolean actual = exerciseDateService.hasEnded(exercise);

        assertThat(actual).isFalse();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testExamExerciseActiveHasEnded() {
        final TextExercise exercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        final Exam exam = exercise.getExerciseGroup().getExam();
        exam.setStartDate(now().minusHours(1));
        exam.setEndDate(now().plusHours(1));
        examRepository.save(exam);
        database.addStudentExam(exam);

        final boolean actual = exerciseDateService.hasEnded(exercise);

        assertThat(actual).isFalse();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testExamExerciseAfterDueHasEnded() {
        final TextExercise exercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        final Exam exam = exercise.getExerciseGroup().getExam();
        exam.setStartDate(now().minusHours(2));
        exam.setEndDate(now().minusHours(1));
        examRepository.save(exam);
        database.addStudentExam(exam);
        final boolean actual = exerciseDateService.hasEnded(exercise);

        assertThat(actual).isTrue();
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

}
