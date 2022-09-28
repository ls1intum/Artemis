package de.tum.in.www1.artemis;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.CourseService;

class DatabaseQueryCountTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseService courseService;

    @BeforeEach
    void setup() {

        database.addUsers(8, 5, 1, 1);

        // Add users that are not in the course
        database.createAndSaveUser("tutor6");
        database.createAndSaveUser("instructor2");
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetAllCoursesForDashboardRealisticQueryCount() throws Exception {
        List<Course> courses = courseRepository.findAll();
        courses.forEach(c -> {
            Course course = courseRepository.findByIdWithExercisesAndLecturesAndLectureUnitsAndLearningGoalsElseThrow(c.getId());
            courseService.delete(course);
        }); // Need to delete all courses so the expected query count is accurate

        // Tests the amount of DB calls for a 'realistic' call to courses/for-dashboard. We should aim to maintain or lower the amount of DB calls, and be aware if they increase
        database.createMultipleCoursesWithAllExercisesAndLectures(10, 10);

        assertThatDb(() -> request.getList("/api/courses/for-dashboard", HttpStatus.OK, Course.class)).hasBeenCalledTimes(34);
    }
}
