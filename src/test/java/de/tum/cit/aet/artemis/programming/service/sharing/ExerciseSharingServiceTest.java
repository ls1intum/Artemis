package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Special tests for ExerciseSharingService focusing on validation failures.
 * Most of the functionality is tested via {@link ExerciseSharingResourceImportTest}
 * and {@link ExerciseSharingResourceExportTest}
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
    void shouldReturnFalseForInvalidTokenAndSecurityString() {
        assertThat(exerciseSharingService.validate("invalidToken", "invalid sec")).isFalse();
        // the token is invalid, however it would return the path
        // assertThat(exerciseSharingService.getExportedExerciseByToken("invalidToken")).isEmpty();
    }

    @Test
    void shouldReturnFalseForNullTokenAndSecurityString() {
        assertThat(exerciseSharingService.validate(null, null)).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken(null)).isEmpty();
    }

    @Test
    void shouldReturnFalseForEmptyTokenAndSecurityString() {
        assertThat(exerciseSharingService.validate("", "")).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken("")).isEmpty();
    }

    @Test
    void shouldReturnFalseForExceedingTokenAndSecurityString() {
        String extraHugeToken = "Something" + "x".repeat(ExerciseSharingService.MAX_EXPORTTOKEN_LENGTH);
        assertThat(exerciseSharingService.validate(extraHugeToken, "")).isFalse();
        assertThat(exerciseSharingService.getExportedExerciseByToken(extraHugeToken)).isEmpty();
    }

    @Test
    void shouldReturnEmptyBasketInfoOnNullToken() {
        assertThat(exerciseSharingService.getBasketInfo(null, "unused")).isEmpty();
    }

    @Test
    void shouldReturnEmptyBasketInfoOnInvalidToken() {
        assertThat(exerciseSharingService.getBasketInfo("&%$!äöü", "unused")).isEmpty();
    }
}
