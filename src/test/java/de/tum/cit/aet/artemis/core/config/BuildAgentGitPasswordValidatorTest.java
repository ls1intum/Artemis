package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.exception.InsecureDefaultCredentialException;

/**
 * Tests for {@link BuildAgentGitPasswordValidator}.
 * <p>
 * The profile test is the point of this class existing separately. The check used to sit in the core-only
 * {@link ConfigurationValidator}, so a node running {@code prod,buildagent} and nothing else - a supported topology,
 * and the one whose {@code application-buildagent.yml} ships {@code buildjob_password} - never ran it.
 */
class BuildAgentGitPasswordValidatorTest {

    private BuildAgentGitPasswordValidator createValidator(boolean productionProfileActive, String buildAgentGitPassword) {
        return createValidator(productionProfileActive, buildAgentGitPassword, false);
    }

    private BuildAgentGitPasswordValidator createValidator(boolean productionProfileActive, String buildAgentGitPassword, boolean buildAgentsUseSsh) {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.matchesProfiles(ArtemisConstants.SPRING_PROFILE_PRODUCTION)).thenReturn(productionProfileActive);
        when(mockEnvironment.getProperty(BuildAgentGitPasswordValidator.BUILD_AGENT_GIT_PASSWORD_PROPERTY)).thenReturn(buildAgentGitPassword);
        when(mockEnvironment.getProperty(Constants.BUILD_AGENT_USE_SSH_PROPERTY_NAME, Boolean.class, false)).thenReturn(buildAgentsUseSsh);
        return new BuildAgentGitPasswordValidator(mockEnvironment);
    }

    /**
     * The regression this class was extracted for: the validator has to be active on a build-agent node, which carries
     * neither the core profile nor the core-only checks. Asserted on the annotation rather than by booting a second
     * Spring context, which would cost minutes to prove one piece of wiring.
     */
    @Test
    void shouldBeActiveOnABuildAgentNodeAsWellAsACoreNode() {
        Profile profile = AnnotationUtils.findAnnotation(BuildAgentGitPasswordValidator.class, Profile.class);

        assertThat(profile).as("the validator must declare the profiles it runs under").isNotNull();
        assertThat(profile.value()).containsExactlyInAnyOrder(Constants.PROFILE_CORE, Constants.PROFILE_BUILDAGENT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "buildjob_password", "buildagent_password", "artemis_admin" })
    void shouldRejectAShippedPasswordUnderTheProductionProfile(String publishedPassword) {
        BuildAgentGitPasswordValidator validator = createValidator(true, publishedPassword);

        assertThatThrownBy(validator::validateBuildAgentGitPassword).isInstanceOf(InsecureDefaultCredentialException.class)
                .hasMessageContaining(BuildAgentGitPasswordValidator.BUILD_AGENT_GIT_PASSWORD_PROPERTY);
    }

    /**
     * A configured but blank password is not an unused property: {@code LocalVCServletService} compares the supplied
     * Basic credentials against it directly, so the published build-agent username with an empty password would be
     * accepted ahead of the rate limit, the authorization checks and the access log.
     *
     * @param blankPassword a configured value that carries no password
     */
    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\t" })
    void shouldRejectABlankPasswordUnderTheProductionProfile(String blankPassword) {
        BuildAgentGitPasswordValidator validator = createValidator(true, blankPassword);

        assertThatThrownBy(validator::validateBuildAgentGitPassword).isInstanceOf(InsecureDefaultCredentialException.class)
                .hasMessageContaining(BuildAgentGitPasswordValidator.BUILD_AGENT_GIT_PASSWORD_PROPERTY);
    }

    /**
     * Only the localvc and buildagent profiles define the property, so its absence means the node has no build-agent
     * shortcut to protect and must still start.
     */
    @Test
    void shouldAcceptAnAbsentPassword() {
        BuildAgentGitPasswordValidator validator = createValidator(true, null);

        assertThatCode(validator::validateBuildAgentGitPassword).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptAUniquePassword() {
        BuildAgentGitPasswordValidator validator = createValidator(true, "a-unique-build-agent-password");

        assertThatCode(validator::validateBuildAgentGitPassword).doesNotThrowAnyException();
    }

    /**
     * Local development, tests and CI run with the packaged defaults on purpose, so the check only applies in
     * production.
     */
    @Test
    void shouldTolerateAShippedPasswordWithoutTheProductionProfile() {
        BuildAgentGitPasswordValidator validator = createValidator(false, "buildjob_password");

        assertThatCode(validator::validateBuildAgentGitPassword).doesNotThrowAnyException();
    }

    /**
     * With ssh configured, {@code LocalVCServletService} rejects the credential pair outright, so this value opens
     * nothing and refusing to start over it would only push operators to invent a password they never use.
     *
     * @param unusedPassword a value that would fail the check if the credential pair were still accepted
     */
    @ParameterizedTest
    @ValueSource(strings = { "buildjob_password", "", " " })
    void shouldTolerateAnUnusedPasswordWhenBuildAgentsAuthenticateWithSsh(String unusedPassword) {
        BuildAgentGitPasswordValidator validator = createValidator(true, unusedPassword, true);

        assertThatCode(validator::validateBuildAgentGitPassword).doesNotThrowAnyException();
    }

    /**
     * The check has to re-arm by itself: it runs on every startup, so the one that follows setting the property back to
     * false must reject the password that the ssh configuration made harmless.
     */
    @Test
    void shouldRejectAShippedPasswordAgainOnceBuildAgentsStopUsingSsh() {
        BuildAgentGitPasswordValidator validator = createValidator(true, "buildjob_password", false);

        assertThatThrownBy(validator::validateBuildAgentGitPassword).isInstanceOf(InsecureDefaultCredentialException.class);
    }
}
