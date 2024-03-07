package de.tum.in.www1.artemis.aspects;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * This abstract class is used to test the aspect that enforces the role of the user for a specific resource.
 * It provides a method to set up the course and other required resources.
 */
public abstract class AbstractEnforceRoleInResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    protected CourseUtilService courseUtilService;

    protected Course course;

    abstract String getTestPrefix();

    abstract String getOtherPrefix();

    /**
     * This method checks if the course is already set up and if not, calls the setupOnce method to set up the course and other required resources.
     */
    @BeforeEach
    void setup() {
        if (course == null) {
            defaultSetup();
            setupOnce();
        }
    }

    /**
     * Creates other required resources.
     */
    abstract void setupOnce();

    /**
     * Creates a course and 4 users (one per role) for the course and 4 users (one per role) for another course.
     */
    void defaultSetup() {
        course = courseUtilService.createCourseWithUserPrefix(getTestPrefix());

        // create users of course
        userUtilService.addUsers(getTestPrefix(), 1, 1, 1, 1);

        // create users of other course
        userUtilService.addStudent(getOtherPrefix() + "students", getOtherPrefix() + "student1");
        userUtilService.addTeachingAssistant(getOtherPrefix() + "tutors", getOtherPrefix() + "tutor1");
        userUtilService.addEditor(getOtherPrefix() + "editors", getOtherPrefix() + "editor1");
        userUtilService.addInstructor(getOtherPrefix() + "instructors", getOtherPrefix() + "instructor1");
    }
}
