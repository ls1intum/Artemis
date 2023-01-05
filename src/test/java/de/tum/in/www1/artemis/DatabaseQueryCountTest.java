package de.tum.in.www1.artemis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;

class DatabaseQueryCountTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String TEST_PREFIX = "databasequerycount";

    @BeforeEach
    void setup() {
        // deactivate to avoid inferences in the query count
        participantScoreSchedulerService.shutdown();
        database.addUsers(TEST_PREFIX, 1, 5, 0, 0);
        // Add users that are not in the course
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        database.createMultipleCoursesWithAllExercisesAndLectures(TEST_PREFIX, 10, 10);
        log.debug("Setup Done. Will count the sql queries now");
        var result = assertThatDb(() -> request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class)).hasBeenCalledAtMostTimes(34);
        log.debug("Number of database queries: {}", result.callCount());
    }
}
