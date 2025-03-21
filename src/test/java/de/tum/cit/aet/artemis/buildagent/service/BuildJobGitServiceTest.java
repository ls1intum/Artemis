package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BuildJobGitServiceTest {

    @InjectMocks
    private BuildJobGitService buildJobGitService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", false);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.of("somePath"));
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", Optional.of("someUrl"));
    }

    @Test
    public void shouldNotUseSshWhenUseSshBuildAgentDisabled() {
        assertThat(buildJobGitService.useSsh()).isFalse();
        buildJobGitService.init();
    }

    @Test
    public void shouldSucceedInitWhenUseSshBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        buildJobGitService.init();
        assertThat(buildJobGitService.useSsh()).isTrue();
        buildJobGitService.init();
    }

    @Test
    public void shouldThrowWhenNoTemplate() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", null);
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> buildJobGitService.init());
    }

    @Test
    public void shouldThrowWhenNoPrivateKey() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.empty());
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> buildJobGitService.init());
    }
}
