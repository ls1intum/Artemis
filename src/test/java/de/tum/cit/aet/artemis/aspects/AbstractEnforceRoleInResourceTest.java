package de.tum.cit.aet.artemis.aspects;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.course.CourseUtilService;
import de.tum.cit.aet.artemis.user.UserUtilService;

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
     * Method is executed before each test to perform default and custom setup.
     */
    @BeforeEach
    void setup() {
        defaultSetup();
        customSetup();
    }

    /**
     * Creates other required resources.
     */
    abstract void customSetup();

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
