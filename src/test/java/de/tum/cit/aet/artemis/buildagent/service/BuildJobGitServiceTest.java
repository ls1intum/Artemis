package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

class BuildJobGitServiceTest extends AbstractArtemisBuildAgentTest {

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", false);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.of("somePath"));
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", Optional.of("someUrl"));
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
}
