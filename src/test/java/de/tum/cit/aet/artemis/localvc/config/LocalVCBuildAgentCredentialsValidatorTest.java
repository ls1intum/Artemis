package de.tum.cit.aet.artemis.localvc.config;

import static de.tum.cit.aet.artemis.core.config.Constants.BUILD_AGENT_USE_SSH_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
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
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Tests for {@link LocalVCBuildAgentCredentialsValidator}.
 * <p>
 * The credentials are optional properties, so nothing else keeps a local VC node from starting without them. Since
 * {@code LocalVCServletService} is lazy, a node in that state would otherwise start cleanly and only reveal the problem
 * when a build agent fails to clone.
 * <p>
 * Which state counts as unusable depends on local CI, and the two roles are opposites. A node running local CI issues a
 * clone token per build job, so a blank pair is the only correct state there and configuring one is refused: it would
 * be a static secret opening every repository, held by every agent and every core node, that nothing needs. A node
 * without local CI has no build jobs, so the pair is the only mechanism left and its absence is fatal instead.
 */
class LocalVCBuildAgentCredentialsValidatorTest {

    private LocalVCBuildAgentCredentialsValidator createValidator(boolean buildAgentsUseSsh, String username, String password, boolean localCiActive) {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getProperty(BUILD_AGENT_USE_SSH_PROPERTY_NAME, Boolean.class, false)).thenReturn(buildAgentsUseSsh);
        when(mockEnvironment.getProperty("artemis.version-control.build-agent-git-username")).thenReturn(username);
        when(mockEnvironment.getProperty("artemis.version-control.build-agent-git-password")).thenReturn(password);
        when(mockEnvironment.matchesProfiles(PROFILE_LOCALCI)).thenReturn(localCiActive);
        return new LocalVCBuildAgentCredentialsValidator(mockEnvironment);
    }

    /**
     * The point of the class: the check has to run at startup rather than when the first git request initialises the
     * lazy {@code LocalVCServletService}. Asserted on the annotations rather than by booting a Spring context, which
     * would cost minutes to prove one piece of wiring.
     */
    @Test
    void shouldRunEagerlyOnALocalVcNode() {
        Component component = AnnotationUtils.findAnnotation(LocalVCBuildAgentCredentialsValidator.class, Component.class);
        Profile profile = AnnotationUtils.findAnnotation(LocalVCBuildAgentCredentialsValidator.class, Profile.class);
        Lazy lazy = AnnotationUtils.findAnnotation(LocalVCBuildAgentCredentialsValidator.class, Lazy.class);

        assertThat(component).as("nothing else registers the validator, so without component scanning it never runs at all").isNotNull();
        assertThat(profile).as("the validator must declare the profile it runs under").isNotNull();
        assertThat(profile.value()).containsExactly(Constants.PROFILE_LOCALVC);
        assertThat(lazy).as("the validator must be eager, otherwise it runs no earlier than the service it protects").isNotNull();
        assertThat(lazy.value()).isFalse();
    }

    /**
     * Without local CI there are no build jobs and therefore no clone tokens, so a node with ssh disabled and an
     * incomplete credential pair accepts nothing at all. This is the Jenkins with LocalVC shape.
     *
     * @param username the configured build-agent git username
     * @param password the configured build-agent git password
     */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = { "null, null", "buildjob_user, null", "null, buildjob_password", "buildjob_user, ''", "'', buildjob_password", "' ', ' '" })
    void shouldRejectAnIncompleteCredentialPairWithoutSshAndWithoutLocalCi(String username, String password) {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(false, username, password, false);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(validator::validateBuildAgentCredentials).withMessageContaining(BUILD_AGENT_USE_SSH_PROPERTY_NAME);
    }

    /**
     * The configuration this change exists to make possible: local CI, no ssh, and no shared secret anywhere. Build
     * agents authenticate with the clone token of the job they are running, so refusing to start here would reject the
     * safest setup rather than an unusable one.
     *
     * @param username the configured build-agent git username
     * @param password the configured build-agent git password
     */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = { "null, null", "'', ''", "' ', ' '" })
    void shouldAcceptABlankCredentialPairWithLocalCi(String username, String password) {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(false, username, password, true);

        assertThatCode(validator::validateBuildAgentCredentials).doesNotThrowAnyException();
    }

    /**
     * A local CI node must not have a shared build-agent credential at all. It needs none - every build job carries a
     * token that covers its own assignment, test, solution and auxiliary repositories - and one that exists is a static
     * secret held by every agent and every core node that opens every repository in the installation.
     * <p>
     * Half a pair is refused too. It cannot authenticate anything, so it is pure configuration debt pointing at a
     * mechanism this node no longer offers.
     *
     * @param username the configured build-agent git username
     * @param password the configured build-agent git password
     */
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = { "buildjob_user, buildjob_password", "buildjob_user, null", "null, buildjob_password", "buildjob_user, ''", "'', buildjob_password" })
    void shouldRejectAnyConfiguredCredentialWithLocalCi(String username, String password) {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(false, username, password, true);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(validator::validateBuildAgentCredentials).withMessageContaining("must not configure a shared");
    }

    /**
     * Refused with ssh as well. The objection is to the credential existing, not to build agents being offered it: with
     * ssh the pair is not used for build agents, but it stays a valid Basic credential on this node, which is exactly
     * what makes leaving one configured a shared secret worth stealing.
     */
    @Test
    void shouldRejectAConfiguredCredentialWithLocalCiEvenWithSsh() {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(true, "buildjob_user", "buildjob_password", true);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(validator::validateBuildAgentCredentials).withMessageContaining("must not configure a shared");
    }

    /**
     * The one setup that still needs the pair: local VC without local CI, which is Jenkins with LocalVC. Jenkins is not
     * an Artemis build agent, so it has neither a key nor a build job to fall back on.
     */
    @Test
    void shouldAcceptACompleteCredentialPairWithoutLocalCi() {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(false, "buildjob_user", "buildjob_password", false);

        assertThatCode(validator::validateBuildAgentCredentials).doesNotThrowAnyException();
    }

    /**
     * With ssh configured the credentials are not accepted at all, so their absence is the expected state rather than a
     * misconfiguration.
     */
    @Test
    void shouldAcceptAMissingCredentialPairWithSsh() {
        LocalVCBuildAgentCredentialsValidator validator = createValidator(true, null, null, false);

        assertThatCode(validator::validateBuildAgentCredentials).doesNotThrowAnyException();
    }
}
