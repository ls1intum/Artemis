package de.tum.cit.aet.artemis.localvc.config;

import static de.tum.cit.aet.artemis.core.config.Constants.BUILD_AGENT_USE_SSH_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Tests for {@link LocalVCBuildAgentCredentialsValidator}.
 * <p>
 * The credentials are optional properties, so nothing else keeps a local VC node from starting without them. Since
 * {@code LocalVCServletService} is lazy, a node in that state would otherwise start cleanly and only reveal the problem
 * when a build agent fails to clone.
 */
class LocalVCBuildAgentCredentialsValidatorTest {

    private LocalVCBuildAgentCredentialsValidator createValidator(boolean buildAgentsUseSsh, String username, String password) {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getProperty(BUILD_AGENT_USE_SSH_PROPERTY_NAME, Boolean.class, false)).thenReturn(buildAgentsUseSsh);
        when(mockEnvironment.getProperty("artemis.version-control.build-agent-git-username")).thenReturn(username);
        when(mockEnvironment.getProperty("artemis.version-control.build-agent-git-password")).thenReturn(password);
        return new LocalVCBuildAgentCredentialsValidator(mockEnvironment);
    }

    /**
     * The point of the class: the check has to run at startup rather than when the first git request initialises the
     * lazy {@code LocalVCServletService}. Asserted on the annotations rather than by booting a Spring context, which
     * would cost minutes to prove one piece of wiring.
     */
    @Test
    void shouldRunEagerlyOnALocalVcNode() {
        Profile profile = AnnotationUtils.findAnnotation(LocalVCBuildAgentCredentialsValidator.class, Profile.class);
        Lazy lazy = AnnotationUtils.findAnnotation(LocalVCBuildAgentCredentialsValidator.class, Lazy.class);

        assertThat(profile).as("the validator must declare the profile it runs under").isNotNull();
        assertThat(profile.value()).containsExactly(Constants.PROFILE_LOCALVC);
        assertThat(lazy).as("the validator must be eager, otherwise it runs no earlier than the service it protects").isNotNull();
        assertThat(lazy.value()).isFalse();
    }

    /**
     * A node with ssh disabled and an incomplete credential pair accepts neither mechanism, so no build agent can ever
     * clone from it.
     *
     * @param username the configured build-agent git username
     * @param password the configured build-agent git password
     */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = { "null, null", "buildjob_user, null", "null, buildjob_password", "buildjob_user, ''", "'', buildjob_password", "' ', ' '" })
    void shouldRejectAnIncompleteCredentialPairWithoutSsh(String username, String password) {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(false, username, password);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(validator::validateBuildAgentCredentials).withMessageContaining(BUILD_AGENT_USE_SSH_PROPERTY_NAME);
    }

    @Test
    void shouldAcceptACompleteCredentialPairWithoutSsh() {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(false, "buildjob_user", "buildjob_password");

        assertThatCode(validator::validateBuildAgentCredentials).doesNotThrowAnyException();
    }

    /**
     * With ssh configured the credentials are not accepted at all, so their absence is the expected state rather than a
     * misconfiguration.
     */
    @Test
    void shouldAcceptAMissingCredentialPairWithSsh() {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(true, null, null);

        assertThatCode(validator::validateBuildAgentCredentials).doesNotThrowAnyException();
    }
}
