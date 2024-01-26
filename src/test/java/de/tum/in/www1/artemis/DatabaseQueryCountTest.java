package de.tum.in.www1.artemis;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CoursesForDashboardDTO;

class DatabaseQueryCountTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryCountTest.class);

    private static final String TEST_PREFIX = "databasequerycount";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void setup() {
        participantScoreScheduleService.shutdown();
        userUtilService.addUsers(TEST_PREFIX, 1, NUMBER_OF_TUTORS, 0, 0);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.setGroups(Set.of(TEST_PREFIX + "tumuser"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        // TODO: add team exercises, do not make all quizzes active
        // TODO: add 1. tutorial groups with a 2. tutorial group configuration, 3. competencies and 4. prerequisites and make sure those are not loaded in the database
        var courses = lectureUtilService.createCoursesWithExercisesAndLecturesAndLectureUnits(TEST_PREFIX, true, true, NUMBER_OF_TUTORS);

        assertThatDb(() -> {
            log.info("Start courses for dashboard call for multiple courses");
            var userCourses = request.get("/api/courses/for-dashboard", HttpStatus.OK, CoursesForDashboardDTO.class);
            log.info("Finish courses for dashboard call for multiple courses");
            return userCourses;
        }).hasBeenCalledAtMostTimes(10);
        // 1 DB call to get the user from the DB
        // 1 DB call to get all active courses
        // 1 DB call to load all exercises
        // 1 DB call to count the exams
        // 1 DB call to count the lectures
        // 1 DB call to get all individual student participations with submissions and results
        // 1 DB call to get all team student participations with submissions and results
        // 1 DB call to get all plagiarism cases
        // 1 DB call to get all grading scales
        // 1 DB call to get the active exams

        var course = courses.get(0);
        assertThatDb(() -> {
            log.info("Start course for dashboard call for one course");
            var userCourse = request.get("/api/courses/" + course.getId() + "/for-dashboard", HttpStatus.OK, Course.class);
            log.info("Finish courses for dashboard call for one course");
            return userCourse;
        }).hasBeenCalledAtMostTimes(11);
        // 1 DB call to get the user from the DB
        // 1 DB call to get the course with lectures
        // 1 DB call to load all exercises with categories
        // 1 DB call to load all exams
        // 3 DB calls to load the numbers of competencies, prerequisites and tutorial groups
        // 1 DB call to get all individual student participations with submissions and results
        // 1 DB call to get all team student participations with submissions and results
        // 1 DB call to get all plagiarism cases
        // 1 DB call to get the grading scale
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamQueryCount() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExamForActiveExamWithUser(TEST_PREFIX + "student1");

        assertThatDb(() -> startWorkingOnExam(studentExam)).hasBeenCalledAtMostTimes(7);
        assertThatDb(() -> submitExam(studentExam)).hasBeenCalledAtMostTimes(3);
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
}
