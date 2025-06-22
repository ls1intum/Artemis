package de.tum.cit.aet.artemis.hyperion;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@Profile(PROFILE_HYPERION)
public abstract class AbstractHyperionIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @BeforeEach
    void setup() {
        // No specific setup needed for Hyperion tests
    }

    @AfterEach
    void tearDown() throws Exception {
        // No specific teardown needed for Hyperion tests
    }
}
