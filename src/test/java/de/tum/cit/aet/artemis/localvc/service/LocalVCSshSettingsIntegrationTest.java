package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.core.util.SessionTestUtil;
import de.tum.cit.aet.artemis.localvc.util.SshSettingsTestService;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class LocalVCSshSettingsIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "sshsettings";

    private static final String SSH_KEY = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJxKWdvcbNTWl4vBjsijoY5HN5dpjxU40huy1PFpdd2o keyComment1 many comments";

    @Autowired
    private SshSettingsTestService sshSettingsTestService;

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private UserSshPublicKeyRepository userSshPublicKeyRepository;

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

    /**
     * Regression test: an SSH key is a permanent credential that survives a password reset, so the session that was in use during an
     * intrusion must not be able to add one after the credentials changed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aSessionThatStartedBeforeTheCredentialsChangedCannotAddAnSshKey() throws Exception {
        User student = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        userActivityService.recordCredentialsChanged(student.getId(), Instant.now());
        SessionTestUtil.actAsSessionStartedAt(student.getLogin(), Instant.now().minus(Duration.ofHours(1)));

        request.postWithResponseBody("/api/programming/ssh-settings/public-keys", SshSettingsTestService.createNewValidSSHKey(student, SSH_KEY), String.class,
                HttpStatus.UNAUTHORIZED);

        assertThat(userSshPublicKeyRepository.findAllByUserId(student.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aSessionThatStartedAfterTheCredentialsChangedCanAddAnSshKey() throws Exception {
        User student = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        userActivityService.recordCredentialsChanged(student.getId(), Instant.now().minus(Duration.ofHours(1)));
        SessionTestUtil.actAsSessionStartedAt(student.getLogin(), Instant.now().minus(Duration.ofMinutes(5)));

        request.postWithResponseBody("/api/programming/ssh-settings/public-keys", SshSettingsTestService.createNewValidSSHKey(student, SSH_KEY), String.class, HttpStatus.OK);

        assertThat(userSshPublicKeyRepository.findAllByUserId(student.getId())).hasSize(1);
    }
}
