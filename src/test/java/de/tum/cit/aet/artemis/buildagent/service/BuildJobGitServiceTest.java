package de.tum.cit.aet.artemis.buildagent.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class BuildJobGitServiceTest {

    @InjectMocks
    private BuildJobGitService buildJobGitService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", false);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.of("somePath"));
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", Optional.of("someUrl"));
    }

    @Test
    public void testUseSshWhenUseSshBuildAgentEnabled() {
        assertFalse(buildJobGitService.useSsh());
        buildJobGitService.init();
    }

    @Test
    public void testInitWhenUseSshBuildAgentEnabled() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        buildJobGitService.init();
        assertTrue(buildJobGitService.useSsh());
        buildJobGitService.init();
    }

    @Test
    public void testThrowsExceptionWhenNoTemplate() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "sshUrlTemplate", null);
        assertThrows(RuntimeException.class, () -> buildJobGitService.init());
    }

    @Test
    public void testThrowsExceptionWhenNoPrivateKey() {
        ReflectionTestUtils.setField(buildJobGitService, "useSshForBuildAgent", true);
        ReflectionTestUtils.setField(buildJobGitService, "gitSshPrivateKeyPath", Optional.empty());
        assertThrows(RuntimeException.class, () -> buildJobGitService.init());
    }
}
