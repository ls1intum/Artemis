package de.tum.in.www1.artemis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;

@Disabled("TODO FIX SERVER TEST: Relies on a database reset since the query count must assume the database is empty. Disabled until we have a performant reset.")
class DatabaseQueryCountTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "databasequerycount";

    @BeforeEach
    void setup() {

        database.addUsers(TEST_PREFIX, 8, 5, 1, 1);

        // Add users that are not in the course
        database.createAndSaveUser(TEST_PREFIX + "tutor6");
        database.createAndSaveUser(TEST_PREFIX + "instructor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        database.createMultipleCoursesWithAllExercisesAndLectures(TEST_PREFIX, 10, 10);

        assertThatDb(() -> request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class)).hasBeenCalledAtMostTimes(34);
    }
}
