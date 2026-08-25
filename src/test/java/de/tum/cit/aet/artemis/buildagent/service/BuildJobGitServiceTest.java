package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

class BuildJobGitServiceTest extends AbstractArtemisBuildAgentTest {

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", false);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.of("somePath"));
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", Optional.of("someUrl"));
        // The service is a shared bean, so the credentials are restored here rather than in the tests that clear them
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitUsername", "buildjob_user");
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitPassword", "buildjob_password");
    }

    @Test
    void shouldNotUseSshWhenUseSshBuildAgentDisabled() {
        assertThat(buildJobGitService.useSsh()).isFalse();
        buildJobGitService.init();
        // should not throw
    }

    @Test
    void shouldSucceedInitWhenUseSshBuildEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        assertThat(buildJobGitService.useSsh()).isTrue();
        buildJobGitService.init();
        // should not throw
    }

    @Test
    void shouldThrowWhenNoTemplateButUseBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", Optional.empty());
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> buildJobGitService.init());
    }

    @Test
    void shouldThrowWhenNoPrivateKeyButUseBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.empty());
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> buildJobGitService.init());
    }

    /**
     * The credentials are optional so that an ssh installation does not have to configure them, which makes the https
     * case responsible for rejecting a missing value. Without this, an agent would start fine and then fail every clone
     * at build time, because the core node refuses a blank credential pair.
     *
     * @param missingCredential the field left unset
     */
    @ParameterizedTest
    @ValueSource(strings = { "buildAgentGitUsername", "buildAgentGitPassword" })
    void shouldThrowWhenCredentialMissingAndUseSshBuildAgentDisabled(String missingCredential) {
        ReflectionTestUtils.setField(buildJobGitService, missingCredential, "");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> buildJobGitService.init()).withMessageContaining(Constants.BUILD_AGENT_USE_SSH_PROPERTY_NAME);
    }

    /**
     * The credentials are unused in the ssh case, so a missing one must not keep the agent from starting.
     */
    @Test
    void shouldSucceedInitWithoutCredentialsWhenUseSshBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitUsername", "");
        ReflectionTestUtils.setField(buildJobGitService, "buildAgentGitPassword", "");

        buildJobGitService.init();
        // should not throw
    }
}
