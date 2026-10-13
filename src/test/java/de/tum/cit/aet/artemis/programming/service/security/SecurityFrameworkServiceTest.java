package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Unit tests for the server-side guards in {@link SecurityFrameworkService}.
 * <p>
 * The Security Framework only supports Java, and the client hides its card for other languages. That gate has to be
 * repeated on the server: the wizard can stage activation while the exercise is Java and then switch the language, which
 * only hides the card and leaves the staged signal intact, so a non-Java exercise can otherwise reach {@code activate}.
 * These tests pin that a non-Java exercise is rejected before the (mocked) Ares2 boundary is ever touched, and that a
 * Java exercise still activates.
 */
@ExtendWith(MockitoExtension.class)
class SecurityFrameworkServiceTest {

    private static final String FRAMEWORK_VERSION = "3.4.1";

    @Mock
    private Ares2SecurityPolicyService ares2SecurityPolicyService;

    @Mock
    private Ares2FrameworkCompatibilityService ares2FrameworkCompatibilityService;

    private SecurityFrameworkService securityFrameworkService;

    @BeforeEach
    void setUp() {
        securityFrameworkService = new SecurityFrameworkService(ares2SecurityPolicyService, ares2FrameworkCompatibilityService);
    }

    private static ProgrammingExercise exerciseWithLanguage(ProgrammingLanguage language) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(language);
        return exercise;
    }

    @Test
    void activateRejectsNonJavaExerciseBeforeTouchingAres2() {
        ProgrammingExercise exercise = exerciseWithLanguage(ProgrammingLanguage.PYTHON);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.activate(exercise, FRAMEWORK_VERSION))
                .matches(exception -> "onlyJavaSupported".equals(exception.getErrorKey()));

        verifyNoInteractions(ares2SecurityPolicyService, ares2FrameworkCompatibilityService);
    }

    @Test
    void updateFrameworkVersionRejectsNonJavaExercise() {
        ProgrammingExercise exercise = exerciseWithLanguage(ProgrammingLanguage.C);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.updateFrameworkVersion(exercise, FRAMEWORK_VERSION))
                .matches(exception -> "onlyJavaSupported".equals(exception.getErrorKey()));

        verifyNoInteractions(ares2SecurityPolicyService, ares2FrameworkCompatibilityService);
    }

    @Test
    void activateSucceedsForJavaExercise() {
        ProgrammingExercise exercise = exerciseWithLanguage(ProgrammingLanguage.JAVA);
        when(ares2FrameworkCompatibilityService.isFrameworkVersionSupported(FRAMEWORK_VERSION)).thenReturn(true);
        when(ares2SecurityPolicyService.createAndCommitPolicy(exercise, FRAMEWORK_VERSION)).thenReturn("deadbee");

        var config = securityFrameworkService.activate(exercise, FRAMEWORK_VERSION);

        assertThat(config.status()).isEqualTo("ACTIVE");
        assertThat(config.frameworkVersion()).isEqualTo(FRAMEWORK_VERSION);
        assertThat(config.lastCommitHash()).isEqualTo("deadbee");
    }
}
