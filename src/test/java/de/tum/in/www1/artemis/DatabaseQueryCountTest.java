package de.tum.in.www1.artemis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;

class DatabaseQueryCountTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String TEST_PREFIX = "databasequerycount";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void setup() {
        participantScoreScheduleService.shutdown();
        userUtilService.addUsers(TEST_PREFIX, 1, NUMBER_OF_TUTORS, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        // TODO: add team exercises, do not make all quizzes active
        var courses = courseUtilService.createMultipleCoursesWithAllExercisesAndLectures(TEST_PREFIX, 1, NUMBER_OF_TUTORS);

        assertThatDb(() -> {
            log.info("Start courses for dashboard call for multiple courses");
            var userCourses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);
            log.info("Finish courses for dashboard call for multiple courses");
            return userCourses;
        }).hasBeenCalledAtMostTimes(11);
        // 1 DB call to get the user from the DB
        // 1 DB call to get the course with exercise, lectures
        // 1 DB call to load all exercises
        // 1 DB call to load all exams
        // 2 DB calls to get the quiz batches for active quiz exercises
        // 1 DB call to get all presentation configurations via grading scales
        // 1 DB call to get all individual student participations with submissions and results
        // 1 DB call to get all team student participations with submissions and results
        // 1 DB call to get all plagiarism cases

        var course = courses.get(0);
        assertThatDb(() -> {
            log.info("Start course for dashboard call for one course");
            var userCourse = request.get("/api/courses/" + course.getId() + "/for-dashboard", HttpStatus.OK, Course.class);
            log.info("Finish courses for dashboard call for one course");
            return userCourse;
        }).hasBeenCalledAtMostTimes(15);
        // 1 DB call to get the user from the DB
        // 2 DB calls to get the course with exercise, lectures, exams
        // 1 DB call to load all exercises
        // 1 DB call to load all exams
        // 1 DB call to load all competencies
        // 1 DB call to load all prerequisite
        // 1 DB call to load all tutorial groups
        // 1 DB call to load the tutorial group configuration
        // 1 DB call to get the presentation configuration via grading scale
        // 1 DB call to get all individual student participations with submissions and results
        // 1 DB call to get all team student participations with submissions and results
        // 2 DB calls to get the quiz batches for active quiz exercises
        // 1 DB call to get all plagiarism cases
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamQueryCount() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExamForActiveExamWithUser(TEST_PREFIX + "student1");

        assertThatDb(() -> startWorkingOnExam(studentExam)).hasBeenCalledAtMostTimes(getStartWorkingOnExamExpectedTotalQueryCount());
        assertThatDb(() -> submitExam(studentExam)).hasBeenCalledAtMostTimes(getSubmitExamExpectedTotalQueryCount());
    }

    private StudentExam startWorkingOnExam(StudentExam studentExam) throws Exception {
        return request.get(
                "/api/courses/" + studentExam.getExam().getCourse().getId() + "/exams/" + studentExam.getExam().getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
    }

    private Void submitExam(StudentExam studentExam) throws Exception {
        request.postWithoutLocation("/api/courses/" + studentExam.getExam().getCourse().getId() + "/exams/" + studentExam.getExam().getId() + "/student-exams/submit", studentExam,
                HttpStatus.OK, null);
        return null;
    }

    private long getStartWorkingOnExamExpectedTotalQueryCount() {
        final int findUserWithGroupsAndAuthoritiesQueryCount = 1;
        final int findStudentExamByIdWithExercisesQueryCount = 1;
        final int findExamByIdWithCourseQueryCount = 1;
        final int updateStudentExamQueryCount = 1;
        final int findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount = 1;
        final int createExamSessionQueryCount = 1;
        final int findExamSessionCountByStudentExamIdQueryCount = 1;
        return findUserWithGroupsAndAuthoritiesQueryCount + findStudentExamByIdWithExercisesQueryCount + findExamByIdWithCourseQueryCount + updateStudentExamQueryCount
                + findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount + createExamSessionQueryCount + findExamSessionCountByStudentExamIdQueryCount;
    }

    private long getSubmitExamExpectedTotalQueryCount() {
        final int findUserWithGroupsAndAuthoritiesQueryCount = 1;
        final int findStudentExamByIdWithExercisesQueryCount = 1;
        final int findExamSessionByStudentExamIdQueryCount = 1;
        final int updateStudentExamQueryCount = 1;
        final int findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount = 1;
        return findUserWithGroupsAndAuthoritiesQueryCount + findStudentExamByIdWithExercisesQueryCount + findExamSessionByStudentExamIdQueryCount + updateStudentExamQueryCount
                + findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount;
    }
}
