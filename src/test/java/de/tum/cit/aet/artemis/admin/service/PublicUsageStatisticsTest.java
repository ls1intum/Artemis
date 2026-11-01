package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.dto.PublicUsageStatisticsDTO;
import de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO;
import de.tum.cit.aet.artemis.admin.repository.PublicUsageStatisticsRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Other tests share the database and clean up their data at arbitrary moments, so these tests never compare global numbers. They
 * only ask the queries about the courses they create themselves.
 */
class PublicUsageStatisticsTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "publicusage";

    private static final int MIN_STUDENTS = PublicUsageStatisticsService.MIN_STUDENTS_PER_COURSE;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Autowired
    private PublicUsageStatisticsRepository publicUsageStatisticsRepository;

    @Autowired
    private PublicUsageStatisticsService publicUsageStatisticsService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private final UsageSemester currentSemester = UsageSemester.containing(ZonedDateTime.now(ZONE).toLocalDate());

    private Set<Long> realCourseIds() {
        return publicUsageStatisticsRepository.findRealCourseIds(currentSemester.start(ZONE), currentSemester.end(ZONE), MIN_STUDENTS);
    }

    private Course createCourseStartingNow(String userPrefix, int students) {
        userUtilService.addUsers(userPrefix, students, 0, 0, 0);
        Course course = courseUtilService.createCourse();
        course.setStartDate(ZonedDateTime.now());
        course.setEndDate(ZonedDateTime.now().plusMonths(3));
        courseUtilService.saveCourse(course);
        userUtilService.enrollPrefixedUsersInCourse(course, userPrefix);
        return course;
    }

    private TextSubmission submission(SubmissionType type) {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("text", Language.ENGLISH, true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(type);
        return submission;
    }

    private TextExercise submitText(Course course, String login, SubmissionType type) {
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2));
        participationUtilService.addSubmission(exercise, submission(type), login);
        return exercise;
    }

    private long submissionsOf(Set<Long> courseIds, String login) {
        Long userId = userTestRepository.findOneByLogin(login).orElseThrow().getId();
        return publicUsageStatisticsRepository.countSubmissionsByStudent(publicUsageStatisticsRepository.findExerciseIdsOfCourses(courseIds)).stream()
                .filter(count -> userId.equals(count.studentId())).mapToLong(StudentSubmissionCountDTO::submissions).sum();
    }

    @Test
    void realCourse_isFoundAndCountedWithStudentsSubmissionsAndExercises() {
        Course course = createCourseStartingNow(TEST_PREFIX + "a", MIN_STUDENTS);
        submitText(course, TEST_PREFIX + "astudent1", SubmissionType.MANUAL);
        submitText(course, TEST_PREFIX + "astudent1", SubmissionType.MANUAL);
        Set<Long> ids = Set.of(course.getId());

        assertThat(realCourseIds()).contains(course.getId());
        assertThat(publicUsageStatisticsRepository.findStudentIds(ids)).hasSize(MIN_STUDENTS);
        assertThat(submissionsOf(ids, TEST_PREFIX + "astudent1")).isEqualTo(2);
        assertThat(publicUsageStatisticsRepository.countExercisesByType(ids)).singleElement().satisfies(entry -> {
            assertThat(entry.exerciseType()).isEqualTo(ExerciseType.TEXT);
            assertThat(entry.value()).isEqualTo(2);
        });
    }

    @Test
    void submissionsCreatedByInstructorsOrTheSystem_doNotCount() {
        Course course = createCourseStartingNow(TEST_PREFIX + "g", MIN_STUDENTS);
        submitText(course, TEST_PREFIX + "gstudent1", SubmissionType.INSTRUCTOR);
        submitText(course, TEST_PREFIX + "gstudent2", SubmissionType.TEST);
        submitText(course, TEST_PREFIX + "gstudent3", SubmissionType.MANUAL);
        Set<Long> ids = Set.of(course.getId());

        assertThat(submissionsOf(ids, TEST_PREFIX + "gstudent1")).isZero();
        assertThat(submissionsOf(ids, TEST_PREFIX + "gstudent2")).isZero();
        assertThat(submissionsOf(ids, TEST_PREFIX + "gstudent3")).isEqualTo(1);
    }

    @Test
    void examExercisesAndTheirSubmissions_belongToTheCourseOfTheExam() {
        Course course = createCourseStartingNow(TEST_PREFIX + "h", MIN_STUDENTS);
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        TextExercise examExercise = textExerciseUtilService.createTextExerciseForExam(exam.getExerciseGroups().getFirst());
        participationUtilService.addSubmission(examExercise, submission(SubmissionType.MANUAL), TEST_PREFIX + "hstudent1");
        Set<Long> ids = Set.of(course.getId());

        assertThat(publicUsageStatisticsRepository.findExerciseIdsOfCourses(ids)).containsExactly(examExercise.getId());
        assertThat(publicUsageStatisticsRepository.countExercisesByType(ids)).singleElement().satisfies(entry -> assertThat(entry.value()).isEqualTo(1));
        assertThat(submissionsOf(ids, TEST_PREFIX + "hstudent1")).isEqualTo(1);
    }

    @Test
    void courseBelowStudentThreshold_isNotFound() {
        Course course = createCourseStartingNow(TEST_PREFIX + "b", MIN_STUDENTS - 1);

        assertThat(realCourseIds()).doesNotContain(course.getId());
    }

    @Test
    void testCourse_isNotFound() {
        Course course = createCourseStartingNow(TEST_PREFIX + "c", MIN_STUDENTS + 1);
        course.setTestCourse(true);
        courseUtilService.saveCourse(course);

        assertThat(realCourseIds()).doesNotContain(course.getId());
    }

    @Test
    void testUsers_doNotCountAsStudentsAndLeaveTheCourseBelowTheThreshold() {
        Course course = createCourseStartingNow(TEST_PREFIX + "d", MIN_STUDENTS);
        User testUser = userTestRepository.findOneByLogin(TEST_PREFIX + "dstudent1").orElseThrow();
        testUser.setTestUser(true);
        userTestRepository.save(testUser);

        assertThat(publicUsageStatisticsRepository.findStudentIds(Set.of(course.getId()))).hasSize(MIN_STUDENTS - 1).doesNotContain(testUser.getId());
        assertThat(realCourseIds()).doesNotContain(course.getId());
    }

    @Test
    void userInTwoRealCourses_isCountedOnce() {
        Course first = createCourseStartingNow(TEST_PREFIX + "e", MIN_STUDENTS);
        Course second = courseUtilService.createCourse();
        second.setStartDate(ZonedDateTime.now());
        courseUtilService.saveCourse(second);
        userUtilService.enrollPrefixedUsersInCourse(second, TEST_PREFIX + "e");

        assertThat(realCourseIds()).contains(first.getId(), second.getId());
        assertThat(publicUsageStatisticsRepository.findStudentIds(Set.of(first.getId(), second.getId()))).hasSize(MIN_STUDENTS);
    }

    @Test
    void courseStartingInThePreviousSemester_isNotFoundInTheCurrentSemester() {
        Course course = createCourseStartingNow(TEST_PREFIX + "f", MIN_STUDENTS);
        course.setStartDate(currentSemester.start(ZONE).minusDays(1));
        courseUtilService.saveCourse(course);

        assertThat(realCourseIds()).doesNotContain(course.getId());
        assertThat(publicUsageStatisticsRepository.findRealCourseIds(currentSemester.previous().start(ZONE), currentSemester.previous().end(ZONE), MIN_STUDENTS))
                .contains(course.getId());
    }

    @Test
    void publicEndpoint_isReachableWithoutAuthentication() throws Exception {
        var statistics = request.get("/api/core/public/usage-statistics", HttpStatus.OK, PublicUsageStatisticsDTO.class);

        assertThat(statistics).isNotNull();
        assertThat(statistics.generatedAt()).isNotNull();
    }

    @Test
    void result_isCachedUntilEvicted() {
        var cache = cacheManager.getCache(PublicUsageStatisticsService.CACHE_NAME);
        assertThat(cache).isNotNull();
        cache.clear();
        var first = publicUsageStatisticsService.getUsageStatistics();

        assertThat(publicUsageStatisticsService.getUsageStatistics().generatedAt()).isEqualTo(first.generatedAt());
    }
}
