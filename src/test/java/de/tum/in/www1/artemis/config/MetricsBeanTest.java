package de.tum.in.www1.artemis.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
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

    private static final String TEST_PREFIX = "metricsbeans";

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    CourseService courseService;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    ExamUserRepository examUserRepository;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    CourseRepository courseRepository;

    @AfterEach
    void resetDatabase() {
        examRepository.findAllByEndDateGreaterThanEqual(ZonedDateTime.now()).forEach(exam -> {
            // Set dates of existing exams to past to that they are not returned in the metrics
            exam.setStartDate(ZonedDateTime.now().minusHours(2));
            exam.setEndDate(ZonedDateTime.now().minusHours(1));
            examRepository.save(exam);
        });
    }

    @Test
    void testPrometheusMetricsExercises() {
        var users = database.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        var course1 = database.createCourse();
        course1.setStudentGroupName(TEST_PREFIX + "students");

        course1.addExercises(exerciseRepository.save(database.createQuiz(course1, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusMinutes(55), QuizMode.SYNCHRONIZED)));
        course1.addExercises(exerciseRepository.save(database.createQuiz(course1, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(3), QuizMode.SYNCHRONIZED)));
        course1.addExercises(exerciseRepository.save(database.createIndividualTextExercise(course1, ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(25), null)));

        // Only one of the two quizzes ends in the next 15 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");
        assertMetricEquals(3 * 1, "artemis.scheduled.exercises.due.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // No active users
        assertMetricEquals(0, "artemis.scheduled.exercises.due.student_multiplier.active.14", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // Add activity to user
        database.saveQuizSubmission(database.getFirstExerciseWithType(course1, QuizExercise.class), ModelFactory.generateQuizSubmission(true), users.get(0).getLogin());

        // Should now have one active user
        assertMetricEquals(1, "artemis.scheduled.exercises.due.student_multiplier.active.14", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // Only one quiz is released within the next 30 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.release.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "30");
        assertMetricEquals(3 * 1, "artemis.scheduled.exercises.release.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "30");

        // Only one active user
        assertMetricEquals(1, "artemis.scheduled.exercises.release.student_multiplier.active.14", "exerciseType", ExerciseType.QUIZ.toString(), "range", "30");

        // Add activity to another user
        database.saveQuizSubmission(database.getFirstExerciseWithType(course1, QuizExercise.class), ModelFactory.generateQuizSubmission(true), users.get(1).getLogin());

        // Should now have two active users
        assertMetricEquals(2, "artemis.scheduled.exercises.release.student_multiplier.active.14", "exerciseType", ExerciseType.QUIZ.toString(), "range", "30");


        // Both quizzes end within the next 120 minutes, but have the same users (-> Users are only counted once)
        assertMetricEquals(2, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "120");
        assertMetricEquals(3 * 1, "artemis.scheduled.exercises.due.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "120");

        database.addUsers(TEST_PREFIX + "2", 3, 0, 0, 0);
        var course2 = database.createCourse();
        course1.setStudentGroupName(TEST_PREFIX + "2" + "students");
        exerciseRepository.save(database.createQuiz(course2, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(3), QuizMode.SYNCHRONIZED));

        // 3 quizzes end within the next 120 minutes, and are in two different courses -> 6 different users in total
        assertMetricEquals(3, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "120");
        assertMetricEquals(3 * 2, "artemis.scheduled.exercises.due.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "120");

        // One text exercise is released within the next 30 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.release.count", "exerciseType", ExerciseType.TEXT.toString(), "range", "30");
    }

    @Test
    void testPrometheusMetricsExams() {
        var users = database.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        var course = database.createCourse();
        var exam1 = database.addExam(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(10), ZonedDateTime.now().plusMinutes(40));
        var registeredExamUser1 = new ExamUser();
        registeredExamUser1.setUser(users.get(0));
        registeredExamUser1.setExam(exam1);
        registeredExamUser1 = examUserRepository.save(registeredExamUser1);
        exam1.addExamUser(registeredExamUser1);

        var registeredExamUser2 = new ExamUser();
        registeredExamUser2.setUser(users.get(1));
        registeredExamUser2.setExam(exam1);
        registeredExamUser2 = examUserRepository.save(registeredExamUser2);
        exam1.addExamUser(registeredExamUser2);

        exam1 = examRepository.save(exam1);

        database.addExerciseGroupsAndExercisesToExam(exam1, false);
        courseRepository.save(course);

        var exam2 = database.addExam(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(65), ZonedDateTime.now().plusMinutes(85));
        var registeredExamUser3 = new ExamUser();
        registeredExamUser3.setUser(users.get(0));
        registeredExamUser3.setExam(exam2);
        registeredExamUser3 = examUserRepository.save(registeredExamUser3);
        exam2.addExamUser(registeredExamUser3);

        exam2 = examRepository.save(exam2);

        database.addExerciseGroupsAndExercisesToExam(exam2, false);
        courseRepository.save(course);

        // One exam ends within the next 60 minutes
        assertMetricEquals(1, "artemis.scheduled.exams.due.count", "range", "60");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.due.student_multiplier", "range", "60"); // 2 students are registered for the exam

        // Two exams ends within the next 120 minutes
        assertMetricEquals(2, "artemis.scheduled.exams.due.count", "range", "120");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.due.student_multiplier", "range", "120"); // 2 + 1 students are registered for the exam, but they are duplicate users

        // No exam starts within the next 5 minutes
        assertMetricEquals(0, "artemis.scheduled.exams.release.count", "range", "5");
        assertMetricEquals(0, "artemis.scheduled.exams.release.student_multiplier", "range", "5");

        // One exam starts within the next 15 minutes
        assertMetricEquals(1, "artemis.scheduled.exams.release.count", "range", "15");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.release.student_multiplier", "range", "15"); // 2 registered students

        // Two exams start within the next 120 minutes, but have the same users (-> Users are only counted once)
        assertMetricEquals(2, "artemis.scheduled.exams.release.count", "range", "120");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.release.student_multiplier", "range", "120");

        var registeredExamUser4 = new ExamUser();
        registeredExamUser4.setUser(users.get(2));
        registeredExamUser4.setExam(exam2);
        registeredExamUser4 = examUserRepository.save(registeredExamUser4);
        exam2.addExamUser(registeredExamUser4);

        exam2 = examRepository.save(exam2);

        // Two exams start within the next 120 minutes, but have the same users (-> Users are only counted once)
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
