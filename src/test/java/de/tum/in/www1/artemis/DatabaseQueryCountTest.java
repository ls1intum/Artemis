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

    @BeforeEach
    void setup() {
        participantScoreSchedulerService.shutdown();
        database.addUsers(TEST_PREFIX, 1, 5, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        String suffix = "cfdr";
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, suffix, 1, 5, 0, 0);
        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        var courses = database.createMultipleCoursesWithAllExercisesAndLectures(TEST_PREFIX, 10, 10);
        database.updateCourseGroups(TEST_PREFIX, courses, suffix);

        assertThatDb(() -> {
            log.info("Start courses for dashboard call");
            var userCourses = request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);
            log.info("Finish courses for dashboard call");
            return userCourses;
        }).hasBeenCalledAtMostTimes(34);
    }
}
