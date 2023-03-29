package de.tum.in.www1.artemis.localvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

public class LocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @BeforeEach
    public void setup() {
        createGitRepositoryWithInitialPush();
    }

    @Test
    public void testBuildAndTestSubmission() {
        // Prepare a Repository that has just received a push.

        // Create a test repository containing Java tests.

        // Call processNewPush.

        // Check that the new result was successfully created.
    }
}
