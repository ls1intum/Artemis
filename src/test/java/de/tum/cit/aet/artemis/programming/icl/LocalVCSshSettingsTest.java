package de.tum.cit.aet.artemis.programming.icl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.programming.icl.util.SshSettingsTestService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class LocalVCSshSettingsTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "sshsettings";

    @Autowired
    private SshSettingsTestService sshSettingsTestService;

    @BeforeEach
    void setUp() throws Exception {
        sshSettingsTestService.setup(TEST_PREFIX);
    }

    @AfterEach
    void teardown() throws Exception {
        Mockito.reset(singleUserNotificationService);
        sshSettingsTestService.tearDown(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getUserSshPublicKeys() throws Exception {
        sshSettingsTestService.getUserSshPublicKeys();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void addSshPublicKeyForUser() throws Exception {
        sshSettingsTestService.addUserSshPublicKey();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void addSshPublicKeyForUserWithoutLabel() throws Exception {
        sshSettingsTestService.addUserSshPublicKeyWithOutLabel();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void failToAddSameSshPublicKeyTwiceForUser() throws Exception {
        sshSettingsTestService.failToAddPublicSSHkeyTwice();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void failToAddOrDeleteSshPublicKeyWithInvalidKeyId() throws Exception {
        sshSettingsTestService.failToAddOrDeleteWithInvalidKeyId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void failToAddInvalidSshPublicKeyForUser() throws Exception {
        sshSettingsTestService.failToAddInvalidPublicSSHkey();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteSshPublicKeyByUser() throws Exception {
        sshSettingsTestService.addAndDeleteSshPublicKey();
    }
}
