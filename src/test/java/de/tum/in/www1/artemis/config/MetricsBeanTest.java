package de.tum.in.www1.artemis.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.user.UserUtilService;
import io.micrometer.core.instrument.MeterRegistry;

class MetricsBeanTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metricsbeans";

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MetricsBean metricsBean;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @BeforeEach
    void resetDatabase() {
        SecurityUtils.setAuthorizationObject();

        examRepository.findAllByEndDateGreaterThanEqual(ZonedDateTime.now()).forEach(exam -> {
            // Set dates of existing exams to past to that they are not returned in the metrics
            exam.setStartDate(ZonedDateTime.now().minusHours(2));
            exam.setEndDate(ZonedDateTime.now().minusHours(1));
            examRepository.save(exam);
        });

        exerciseRepository.findAll().forEach(exercise -> {
            // Set dates of existing exercises to past to that they are not returned in the metrics
            exercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
            exercise.setStartDate(ZonedDateTime.now().minusHours(2));
            exercise.setDueDate(ZonedDateTime.now().minusHours(1));
            exerciseRepository.save(exercise);
        });
    }

    @Test
    void testPublicMetricsUpdatedWhenTriggered() {
        metricsBean.updatePublicArtemisMetrics();
        var courseCountBefore = courseRepository.count();
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        courseUtilService.createCourse();

        // This is still the old value because the update function has not been called yet
        assertMetricEquals(courseCountBefore, "artemis.statistics.public.courses");

        metricsBean.updatePublicArtemisMetrics();

        // After the update: the course is returned
        assertMetricEquals(courseCountBefore + 1, "artemis.statistics.public.courses");
    }

    @Test
    void testPublicMetricsActiveUsers() {
        var submissions = submissionRepository.findAll();
        submissions.forEach(submission -> submission.setSubmissionDate(ZonedDateTime.now().minusDays(30)));
        submissionRepository.saveAll(submissions);

        var users = userUtilService.addUsers(TEST_PREFIX + "active", 3, 0, 0, 0);

        var course1 = textExerciseUtilService.addCourseWithOneFinishedTextExercise();
        course1.setStudentGroupName(TEST_PREFIX + "active" + "tumuser");
        courseRepository.save(course1);

        var textExercise = exerciseUtilService.getFirstExerciseWithType(course1, TextExercise.class);
        textExercise.setStartDate(ZonedDateTime.now().minusDays(40));

        var result1 = participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), users.get(0), 10.0, 0.0, 50, true);
        result1.getSubmission().setSubmissionDate(ZonedDateTime.now().minusMinutes(10));
        submissionRepository.save(result1.getSubmission());

        var result2 = participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), users.get(1), 10.0, 0.0, 50, true);
        result2.getSubmission().setSubmissionDate(ZonedDateTime.now().minusDays(5));
        submissionRepository.save(result2.getSubmission());

        var result3 = participationUtilService.createParticipationSubmissionAndResult(textExercise.getId(), users.get(2), 10.0, 0.0, 50, true);
        result3.getSubmission().setSubmissionDate(ZonedDateTime.now().minusDays(20));
        submissionRepository.save(result3.getSubmission());

        metricsBean.updatePublicArtemisMetrics();

        assertMetricEquals(1, "artemis.statistics.public.active_users", "period", "1");
        assertMetricEquals(2, "artemis.statistics.public.active_users", "period", "7");
        assertMetricEquals(2, "artemis.statistics.public.active_users", "period", "14");
        assertMetricEquals(3, "artemis.statistics.public.active_users", "period", "30");
    }

    @Test
    void testPublicMetricsCourses() {
        var activeCourse = courseUtilService.createCourse();
        activateCourse(activeCourse);
        courseRepository.save(activeCourse);

        var inactiveCourse = courseUtilService.createCourse();
        inactiveCourse.setStartDate(ZonedDateTime.now().minusDays(2));
        inactiveCourse.setEndDate(ZonedDateTime.now().minusDays(1));
        courseRepository.save(inactiveCourse);

        metricsBean.updatePublicArtemisMetrics();

        long totalNumberOfCourses = courseRepository.count();
        long numberOfActiveCourses = countActiveCourses();

        // Assert that there is at least one non-active course in the database so that the values returned from the metrics are different
        assertThat(numberOfActiveCourses).isNotEqualTo(totalNumberOfCourses);

        assertMetricEquals(totalNumberOfCourses, "artemis.statistics.public.courses");
        assertMetricEquals(numberOfActiveCourses, "artemis.statistics.public.active_courses");
    }

    @Test
    void testPublicMetricsFilterTestCourses() {
        var activeCourse = courseUtilService.createCourse();
        activateCourse(activeCourse);
        courseRepository.save(activeCourse);

        var testCourse = courseUtilService.createCourse();
        activateCourse(testCourse);
        testCourse.setTestCourse(true);
        courseRepository.save(testCourse);

        metricsBean.updatePublicArtemisMetrics();

        long totalNumberOfCourses = courseRepository.count();
        long numberOfActiveCourses = countActiveCourses();

        assertMetricEquals(totalNumberOfCourses, "artemis.statistics.public.courses");
        assertMetricEquals(numberOfActiveCourses, "artemis.statistics.public.active_courses");
    }

    private long countActiveCourses() {
        final List<Course> activeCourses = courseRepository.findAllActive(ZonedDateTime.now());
        // the test courses are only filtered for the metrics since for instructors/tutors/editors using Artemis
        // test courses count as active, but they never contain active students/exams relevant for the metrics
        return activeCourses.stream().filter(course -> !course.isTestCourse()).count();
    }

    @Test
    void testPublicMetricsExams() {
        var users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        var courseWithActiveExam = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(users.get(0));
        var activeExam = examRepository.findByCourseId(courseWithActiveExam.getId()).get(0);
        activeExam.setStartDate(ZonedDateTime.now().minusDays(1));
        activeExam.setEndDate(ZonedDateTime.now().plusDays(1));
        examRepository.save(activeExam);

        var courseWithInactiveExam = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(users.get(0));
        var inactiveExam = examRepository.findByCourseId(courseWithInactiveExam.getId()).get(0);
        inactiveExam.setStartDate(ZonedDateTime.now().minusDays(1));
        inactiveExam.setEndDate(ZonedDateTime.now().plusDays(1));
        examRepository.save(inactiveExam);

        metricsBean.updatePublicArtemisMetrics();

        long totalNumberOfExams = examRepository.count();
        long numberOfActiveExams = examRepository.countAllActiveExams(ZonedDateTime.now());

        // Assert that there is at least one non-active exam in the database so that the values returned from the metrics are different
        assertThat(numberOfActiveExams).isNotEqualTo(totalNumberOfExams);

        assertMetricEquals(totalNumberOfExams, "artemis.statistics.public.exams");
        assertMetricEquals(numberOfActiveExams, "artemis.statistics.public.active_exams");
    }

    @Test
    void testPublicMetricsCourseAndExamStudents() {
        var users = userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);

        var course1 = courseUtilService.createCourse();
        course1.setTitle("Course 1");
        course1.setSemester(null);
        course1.setStudentGroupName(TEST_PREFIX + "course1Students");
        course1 = courseRepository.save(course1);
        var exam1 = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        exam1.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exam1.setTitle("exam" + UUID.randomUUID());
        examRepository.save(exam1);
        examUtilService.addStudentExamWithUser(exam1, users.get(0));
        examUtilService.addStudentExamWithUser(exam1, users.get(1));

        var course2 = courseUtilService.createCourse();
        course2.setSemester("WS 2023/24");
        course2.setStudentGroupName(TEST_PREFIX + "course2Students");
        course2 = courseRepository.save(course2);
        var exam2 = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course2);
        exam2.setTitle("exam" + UUID.randomUUID());
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam2);
        examUtilService.addStudentExamWithUser(exam2, users.get(0));

        users.forEach(user -> user.setGroups(new HashSet<>()));
        users.get(0).getGroups().add(TEST_PREFIX + "course1Students");
        users.get(1).getGroups().add(TEST_PREFIX + "course1Students");
        users.get(2).getGroups().add(TEST_PREFIX + "course1Students");
        users.get(2).getGroups().add(TEST_PREFIX + "course2Students");
        userRepository.saveAll(users);

        metricsBean.updatePublicArtemisMetrics();

        String course1Id = Long.toString(course1.getId());
        String course2Id = Long.toString(course2.getId());

        assertMetricEquals(3, "artemis.statistics.public.course_students", "courseId", course1Id, "courseName", course1.getTitle(), "semester", "No semester");
        assertMetricEquals(1, "artemis.statistics.public.course_students", "courseId", course2Id, "courseName", course2.getTitle(), "semester", "WS 2023/24");

        assertMetricEquals(2, "artemis.statistics.public.exam_students", "courseId", course1Id, "courseName", course1.getTitle(), "examId", Long.toString(exam1.getId()),
                "examName", exam1.getTitle(), "semester", "No semester");
        assertMetricEquals(1, "artemis.statistics.public.exam_students", "courseId", course2Id, "examId", Long.toString(exam2.getId()), "examName", exam2.getTitle(), "semester",
                "WS 2023/24");
    }

    @Test
    void testPublicMetricsExercises() {
        var course1 = courseUtilService.createCourse();
        // Active quiz
        course1.addExercises(exerciseRepository
                .save(QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.SYNCHRONIZED, course1)));
        // Not active quiz (in past)
        course1.addExercises(exerciseRepository
                .save(QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED, course1)));

        // Active text exercise
        course1.addExercises(exerciseRepository.save(
                textExerciseUtilService.createIndividualTextExercise(course1, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(1))));
        // Not active text exercise (in future)
        course1.addExercises(exerciseRepository.save(
                textExerciseUtilService.createIndividualTextExercise(course1, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(3))));

        courseRepository.save(course1);

        metricsBean.updatePublicArtemisMetrics();

        assertMetricEquals(1, "artemis.statistics.public.active_exercises", "exerciseType", ExerciseType.QUIZ.toString());
        assertMetricEquals(1, "artemis.statistics.public.active_exercises", "exerciseType", ExerciseType.TEXT.toString());

        assertMetricEquals(
                exerciseRepository.countExercisesGroupByExerciseType().stream().filter(e -> e.exerciseType() == ExerciseType.QUIZ.getExerciseClass()).findFirst().get().value(),
                "artemis.statistics.public.exercises", "exerciseType", ExerciseType.QUIZ.toString());
        assertMetricEquals(
                exerciseRepository.countExercisesGroupByExerciseType().stream().filter(e -> e.exerciseType() == ExerciseType.TEXT.getExerciseClass()).findFirst().get().value(),
                "artemis.statistics.public.exercises", "exerciseType", ExerciseType.TEXT.toString());
    }

    @Test
    void testPrometheusMetricsExercises() {
        var users = userUtilService.addUsers(TEST_PREFIX, 6, 0, 0, 0);
        // 3 in course 1
        users.get(0).setGroups(Set.of(TEST_PREFIX + "course1students"));
        users.get(1).setGroups(Set.of(TEST_PREFIX + "course1students"));
        users.get(2).setGroups(Set.of(TEST_PREFIX + "course1students"));
        // 3 in course 2
        users.get(3).setGroups(Set.of(TEST_PREFIX + "course2students"));
        users.get(4).setGroups(Set.of(TEST_PREFIX + "course2students"));
        users.get(5).setGroups(Set.of(TEST_PREFIX + "course2students"));
        userRepository.saveAll(users);

        var course1 = courseUtilService.createCourse();
        course1.setStudentGroupName(TEST_PREFIX + "course1students");

        course1.addExercises(exerciseRepository
                .save(QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().plusMinutes(5), ZonedDateTime.now().plusMinutes(55), QuizMode.SYNCHRONIZED, course1)));
        course1.addExercises(exerciseRepository
                .save(QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(3), QuizMode.SYNCHRONIZED, course1)));
        course1.addExercises(exerciseRepository
                .save(textExerciseUtilService.createIndividualTextExercise(course1, ZonedDateTime.now().plusMinutes(1), ZonedDateTime.now().plusMinutes(12), null)));
        courseRepository.save(course1);

        metricsBean.recalculateMetrics();

        // Only one of the two quizzes ends in the next 15 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");
        assertMetricEquals(3 * 1, "artemis.scheduled.exercises.due.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // No active users
        assertMetricEquals(0, "artemis.scheduled.exercises.due.student_multiplier.active.14", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // Add activity to user
        quizExerciseUtilService.saveQuizSubmission(exerciseUtilService.getFirstExerciseWithType(course1, QuizExercise.class), ParticipationFactory.generateQuizSubmission(true),
                users.get(0).getLogin());

        // We have to first refresh the active users and then the metrics to ensure the data is updated correctly
        metricsBean.calculateCachedActiveUserNames();
        metricsBean.recalculateMetrics();

        // Should now have one active user
        assertMetricEquals(1, "artemis.scheduled.exercises.due.student_multiplier.active.14", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // Two quizzes are released within the next 15 minutes, but have the same users (-> Users are only counted once)
        assertMetricEquals(2, "artemis.scheduled.exercises.release.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");
        assertMetricEquals(3 * 1, "artemis.scheduled.exercises.release.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // Add activity to another user
        quizExerciseUtilService.saveQuizSubmission(exerciseUtilService.getFirstExerciseWithType(course1, QuizExercise.class), ParticipationFactory.generateQuizSubmission(true),
                users.get(1).getLogin());

        // We have to first refresh the active users and then the metrics to ensure the data is updated correctly
        metricsBean.calculateCachedActiveUserNames();
        metricsBean.recalculateMetrics();

        // Should now have two active users
        assertMetricEquals(2, "artemis.scheduled.exercises.release.student_multiplier.active.14", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        var course2 = courseUtilService.createCourse();
        course2.setStudentGroupName(TEST_PREFIX + "course2students");
        courseRepository.save(course2);
        course2.addExercises(
                exerciseRepository.save(QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(3), QuizMode.SYNCHRONIZED, course2)));
        courseRepository.save(course2);

        metricsBean.recalculateMetrics();

        // 2 quizzes end within the next 15 minutes, and are in two different courses -> 6 different users in total
        assertMetricEquals(2, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");
        assertMetricEquals(3 * 2, "artemis.scheduled.exercises.due.student_multiplier", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");

        // One text exercise is released within the next 30 minutes
        assertMetricEquals(1, "artemis.scheduled.exercises.release.count", "exerciseType", ExerciseType.TEXT.toString(), "range", "15");
    }

    @Test
    void testPrometheusMetricsExams() {
        var users = userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        var course = courseUtilService.createCourse();
        var exam1 = examUtilService.addExam(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(2), ZonedDateTime.now().plusMinutes(10));
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

        examUtilService.addExerciseGroupsAndExercisesToExam(exam1, false);
        courseRepository.save(course);

        var exam2 = examUtilService.addExam(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(5), ZonedDateTime.now().plusMinutes(12));
        var registeredExamUser3 = new ExamUser();
        registeredExamUser3.setUser(users.get(0));
        registeredExamUser3.setExam(exam2);
        registeredExamUser3 = examUserRepository.save(registeredExamUser3);
        exam2.addExamUser(registeredExamUser3);

        exam2 = examRepository.save(exam2);

        examUtilService.addExerciseGroupsAndExercisesToExam(exam2, false);
        courseRepository.save(course);

        metricsBean.recalculateMetrics();

        // Two exams start within the next 15 minutes, but have the same users (-> Users are only counted once)
        assertMetricEquals(2, "artemis.scheduled.exams.release.count", "range", "15");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.release.student_multiplier", "range", "15");

        // Two exams ends within the next 120 minutes
        assertMetricEquals(2, "artemis.scheduled.exams.due.count", "range", "15");
        assertMetricEquals(1 * 2, "artemis.scheduled.exams.due.student_multiplier", "range", "15"); // 2 + 1 students are registered for the exam, but they are duplicate users

        var registeredExamUser4 = new ExamUser();
        registeredExamUser4.setUser(users.get(2));
        registeredExamUser4.setExam(exam2);
        registeredExamUser4 = examUserRepository.save(registeredExamUser4);
        exam2.addExamUser(registeredExamUser4);

        examRepository.save(exam2);

        metricsBean.recalculateMetrics();

        // Two exams start within the next 15 minutes, but have the same users (-> Users are only counted once)
        assertMetricEquals(2, "artemis.scheduled.exams.release.count", "range", "15");
        assertMetricEquals(1 * 2 + 1 * 1, "artemis.scheduled.exams.release.student_multiplier", "range", "15");

        // Exam exercises are not returned in the exercises metrics
        assertMetricEquals(0, "artemis.scheduled.exercises.due.count", "exerciseType", ExerciseType.QUIZ.toString(), "range", "15");
    }

    @Test
    void testEnsureCourseInformationIsSet() {
        var course = courseUtilService.addEmptyCourse();
        course.setTitle(null);
        course.setShortName("metricCourseShort");
        course.setSemester(null);

        courseRepository.save(course);

        metricsBean.updatePublicArtemisMetrics();

        var metricValue = meterRegistry.get("artemis.statistics.public.course_students").tags("semester", "No semester", "courseName", "CoursemetricCourseShort").gauge().value();
        assertThat(metricValue).isGreaterThan(0L);
    }

    private void assertMetricEquals(double expectedValue, String metricName, String... tags) {
        var gauge = meterRegistry.get(metricName).tags(tags).gauge();
        assertThat(gauge.value()).isEqualTo(expectedValue);
    }

    private void activateCourse(final Course course) {
        course.setStartDate(ZonedDateTime.now().minusDays(1));
        course.setEndDate(ZonedDateTime.now().plusDays(1));
    }
}
