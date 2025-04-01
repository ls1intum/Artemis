package de.tum.cit.aet.artemis.exercise.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.sharing.ExerciseSharingService;

/**
 * some special tests for ExerciseSharingService.
 * Most of functionality is tested via @link ExerciseSharingResourceImportTest and @Link ExerciseSharingResourceExportTest
 */
class ExerciseSharingServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private SharingPlatformMockProvider sharingPlatformMockProvider;

    @Autowired
    private ExerciseSharingService exerciseSharingService;

    @BeforeEach
    void startUp() throws Exception {
        sharingPlatformMockProvider.connectRequestFromSharingPlattform();
    }

    @AfterEach
    void tearDown() throws Exception {
        sharingPlatformMockProvider.reset();
    }

    @Test
    void validationFailure() {
        assertThat(exerciseSharingService.validate("invalidToken", "invalid sec")).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken("invalidToken")).isNull();
    }

    @Test
    void requestValidationTests() {
        assertThat(exerciseSharingService.validate("invalidToken", "invalid sec")).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken("invalidToken")).isNull();
    }

}
