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

public class MetricsBeanTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    public void testPrometheusMetricsExercises() {
        database.addUsers(3, 0, 0, 0);
        var course = database.createCourse();
        exerciseRepository.save(database.createQuiz(course, ZonedDateTime.now().plusMinutes(25), ZonedDateTime.now().plusMinutes(55), QuizMode.SYNCHRONIZED));
        exerciseRepository.save(database.createQuiz(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(3), QuizMode.SYNCHRONIZED));
        exerciseRepository.save(database.createIndividualTextExercise(course, ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(25), null));

        // Only one of the two quizzes ends in the next 15 minutes
        var gauge = meterRegistry.get("artemis.scheduled.exercises.due.count").tag("exerciseType", ExerciseType.QUIZ.toString()).tag("range", "15").gauge();
        assertEquals(1, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exercises.due.student_multiplier").tag("exerciseType", ExerciseType.QUIZ.toString()).tag("range", "15").gauge();
        assertEquals(3 * 1, gauge.value());

        // Only one quiz is released within the next 30 minutes
        gauge = meterRegistry.get("artemis.scheduled.exercises.release.count").tag("exerciseType", ExerciseType.QUIZ.toString()).tag("range", "30").gauge();
        assertEquals(1, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exercises.release.student_multiplier").tag("exerciseType", ExerciseType.QUIZ.toString()).tag("range", "30").gauge();
        assertEquals(3 * 1, gauge.value());

        // Both quizzes end within the next 120 minutes
        gauge = meterRegistry.get("artemis.scheduled.exercises.due.count").tag("exerciseType", ExerciseType.QUIZ.toString()).tag("range", "120").gauge();
        assertEquals(2, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exercises.due.student_multiplier").tag("exerciseType", ExerciseType.QUIZ.toString()).tag("range", "120").gauge();
        assertEquals(3 * 2, gauge.value());

        // One text exercise is released within the next 30 minutes
        gauge = meterRegistry.get("artemis.scheduled.exercises.release.count").tag("exerciseType", ExerciseType.TEXT.toString()).tag("range", "30").gauge();
        assertEquals(1, gauge.value());
    }

    @Test
    public void testPrometheusMetricsExams() {
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
        var gauge = meterRegistry.get("artemis.scheduled.exams.due.count").tag("range", "60").gauge();
        assertEquals(1, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exams.due.student_multiplier").tag("range", "60").gauge();
        assertEquals(1 * 2, gauge.value()); // 2 students are registered for the exam

        // Two exams ends within the next 120 minutes
        gauge = meterRegistry.get("artemis.scheduled.exams.due.count").tag("range", "120").gauge();
        assertEquals(2, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exams.due.student_multiplier").tag("range", "120").gauge();
        assertEquals(1 * 2 + 1 * 1, gauge.value()); // 2 + 1 students are registered for the exam

        // No exam starts within the next 5 minutes
        gauge = meterRegistry.get("artemis.scheduled.exams.release.count").tag("range", "5").gauge();
        assertEquals(0, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exams.release.student_multiplier").tag("range", "5").gauge();
        assertEquals(0, gauge.value());

        // One exam starts within the next 15 minutes
        gauge = meterRegistry.get("artemis.scheduled.exams.release.count").tag("range", "15").gauge();
        assertEquals(1, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exams.release.student_multiplier").tag("range", "15").gauge();
        assertEquals(1 * 2, gauge.value()); // 2 registered students

        // Two exams start within the next 120 minutes
        gauge = meterRegistry.get("artemis.scheduled.exams.release.count").tag("range", "120").gauge();
        assertEquals(2, gauge.value());
        gauge = meterRegistry.get("artemis.scheduled.exams.release.student_multiplier").tag("range", "120").gauge();
        assertEquals(1 * 2 + 1 * 1, gauge.value());

        // Exam exercises are not returned in the exercises metrics
        gauge = meterRegistry.get("artemis.scheduled.exercises.due.count").tag("exerciseType", ExerciseType.QUIZ.toString()).tag("range", "60").gauge();
        assertEquals(0, gauge.value());
    }
}
