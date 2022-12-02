package de.tum.in.www1.artemis.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.CourseService;
import io.micrometer.core.instrument.MeterRegistry;

class MetricsBeanTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    CourseService courseService;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    CourseRepository courseRepository;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    void testPrometheusMetricsExercises() {
        database.addUsers(3, 0, 0, 0);
        var course = database.createCourse();
        exerciseRepository.save(database.createQuiz(course, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusMinutes(55), QuizMode.SYNCHRONIZED));
        exerciseRepository.save(database.createQuiz(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(3), QuizMode.SYNCHRONIZED));
        exerciseRepository.save(database.createIndividualTextExercise(course, ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(25), null));

        // Only one of the two quizzes ends in the next 15 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");
        assertMetricEquals(3 * 1, "artemis.scheduled.exercises.due.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // Only one quiz is released within the next 30 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.release.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "30");
        assertMetricEquals(3 * 1, "artemis.scheduled.exercises.release.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "30");

        // Both quizzes end within the next 120 minutes
        assertMetricEquals(2, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "120");
        assertMetricEquals(3 * 2, "artemis.scheduled.exercises.due.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "120");

        // One text exercise is released within the next 30 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.release.count", "exerciseType", ExerciseType.TEXT.toString(), "range", "30");
    }

    @Test
    void testPrometheusMetricsExams() {
        var users = database.addUsers(3, 0, 0, 0);
        var course = database.createCourse();
        var exam1 = database.addExam(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(10), ZonedDateTime.now().plusMinutes(40));
        exam1.addRegisteredUser(users.get(0));
        exam1.addRegisteredUser(users.get(1));
        database.addExerciseGroupsAndExercisesToExam(exam1, false);
        courseRepository.save(course);

        var exam2 = database.addExam(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(65), ZonedDateTime.now().plusMinutes(85));
        exam2.addRegisteredUser(users.get(0));
        database.addExerciseGroupsAndExercisesToExam(exam2, false);
        courseRepository.save(course);

        // One exam ends within the next 60 minutes
        assertMetricEquals(1, "artemis.scheduled.exams.due.count", "range", "60");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.due.student_multiplier", "range", "60"); // 2 students are registered for the exam

        // Two exams ends within the next 120 minutes
        assertMetricEquals(2, "artemis.scheduled.exams.due.count", "range", "120");
        assertMetricEquals(1 * 2 + 1 * 1, "artemis.scheduled.exams.due.student_multiplier", "range", "120"); // 2 + 1 students are registered for the exam

        // No exam starts within the next 5 minutes
        assertMetricEquals(0, "artemis.scheduled.exams.release.count", "range", "5");
        assertMetricEquals(0, "artemis.scheduled.exams.release.student_multiplier", "range", "5");

        // One exam starts within the next 15 minutes
        assertMetricEquals(1, "artemis.scheduled.exams.release.count", "range", "15");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.release.student_multiplier", "range", "15"); // 2 registered students

        // Two exams start within the next 120 minutes
        assertMetricEquals(2, "artemis.scheduled.exams.release.count", "range", "120");
        assertMetricEquals(1 * 2 + 1 * 1, "artemis.scheduled.exams.release.student_multiplier", "range", "120");

        // Exam exercises are not returned in the exercises metrics
        assertMetricEquals(0, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "60");
    }

    private void assertMetricEquals(double expectedValue, String metricName, String... tags) {
        var gauge = meterRegistry.get(metricName).tags(tags).gauge();
        assertEquals(expectedValue, gauge.value());
    }
}
