package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.yannbriancon.interceptor.HibernateQueryInterceptor;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class DatabaseQueryCountTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private HibernateQueryInterceptor hibernateQueryInterceptor;

    private final static int numberOfStudents = 8;

    private final static int numberOfTutors = 5;

    private final static int numberOfEditors = 1;

    private final static int numberOfInstructors = 1;

    @BeforeEach
    public void setup() {

        database.addUsers(numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("tutor6"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        database.createMultipleCoursesWithAllExercisesAndLectures(10, 10);

        hibernateQueryInterceptor.startQueryCount();

        request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class);

        assertThat(hibernateQueryInterceptor.getQueryCount()).isEqualTo(34);
    }
}
