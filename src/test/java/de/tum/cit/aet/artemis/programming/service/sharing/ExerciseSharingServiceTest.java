package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Special tests for ExerciseSharingService focusing on validation failures.
 * Most of the functionality is tested via {@link de.tum.cit.aet.artemis.programming.web.rest.ExerciseSharingResourceImportTest}
 * and {@link de.tum.cit.aet.artemis.programming.web.rest.ExerciseSharingResourceExportTest}
 */
class ExerciseSharingServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private ExerciseSharingService exerciseSharingService;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlatform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    @Test
    void testValidationFailure() {
        assertThat(exerciseSharingService.validate("invalidToken", "invalid sec")).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken("invalidToken")).isNull();
    }

}
