package de.tum.in.www1.artemis.aspects;

import org.junit.jupiter.api.BeforeEach;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;

/**
 * This abstract class is used to test the aspect that enforces the role of the user for a specific resource.
 * It provides a method to set up the course and other required resources.
 */
public abstract class AbstractEnforceRoleInResourceTest extends AbstractSpringIntegrationIndependentTest {

    protected Course course;

    /**
     * This method checks if the course is already set up and if not, calls the setupOnce method to set up the course and other required resources.
     */
    @BeforeEach
    void setup() {
        if (course == null) {
            setupOnce();
        }
    }

    /**
     * This method is called once to set up the course and other required resources.
     */
    abstract void setupOnce();

}
