package de.tum.in.www1.artemis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;

class DatabaseQueryCountTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String TEST_PREFIX = "databasequerycount";

    private static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void setup() {
        participantScoreScheduleService.shutdown();
        database.addUsers(TEST_PREFIX, 1, NUMBER_OF_TUTORS, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        String suffix = "cfdr";
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, suffix, 1, NUMBER_OF_TUTORS, 0, 0);
        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        // TODO: add team exercises, do not make all quizzes active
        var courses = database.createMultipleCoursesWithAllExercisesAndLectures(TEST_PREFIX, 10, 10, NUMBER_OF_TUTORS);
        database.updateCourseGroups(TEST_PREFIX, courses, suffix);

        assertThatDb(() -> {
            log.info("Start courses for dashboard call for multiple courses");
            var userCourses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);
            log.info("Finish courses for dashboard call for multiple courses");
            return userCourses;
        }).hasBeenCalledAtMostTimes(27);
        // 1 DB call to get the user from the DB
        // 1 DB call to get the course with exercise, lectures
        // 1 DB call to load all exercises
        // 1 DB call to load all exams
        // 10 DB calls to get the quiz batches for active quiz exercises
        // 10 DB calls to get the presentation configuration for each course
        // 1 DB call to get all individual student participations with submissions and results
        // 1 DB call to get all team student participations with submissions and results
        // 1 DB call to get all plagiarism cases

        var course = courses.get(0);
        assertThatDb(() -> {
            log.info("Start course for dashboard call for one course");
            var userCourse = request.get("/api/courses/" + course.getId() + "/for-dashboard", HttpStatus.OK, Course.class);
            log.info("Finish courses for dashboard call for one course");
            return userCourse;
        }).hasBeenCalledAtMostTimes(12);
        // 1 DB call to get the user from the DB
        // 1 DB call to get the course with exercise, lectures, exams
        // 1 DB call to load all exercises
        // 1 DB call to load all exams
        // 1 DB call to load all competencies
        // 1 DB call to load all prerequisite
        // 1 DB call to load all tutorial groups
        // 1 DB call to load the tutorial group configuration
        // 1 DB call to get the presentation configuration
        // 1 DB call to get all individual student participations with submissions and results
        // 1 DB call to get all team student participations with submissions and results
        // 1 DB call to get all plagiarism cases
    }
}
