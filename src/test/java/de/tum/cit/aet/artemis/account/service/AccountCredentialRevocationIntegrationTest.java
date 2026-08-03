package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies against a real database that the account lifecycle transitions actually revoke the credentials that can be
 * used instead of the password.
 * <p>
 * These are the cases where a silent gap is expensive: a user who resets their password because they suspect a
 * compromise, and an administrator who deactivates an account. Each one is asserted per credential type, because the
 * failure mode is one type being forgotten - which is how passkeys and the personal VCS access token came to be missing
 * from the soft-delete cleanup in the first place.
 */
class AccountCredentialRevocationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "credentialrevocation";

    @Autowired
    private AccountCredentialRevocationService accountCredentialRevocationService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSshPublicKeyRepository userSshPublicKeyRepository;

    @Autowired
    private UserUtilService userUtilService;

    private User user;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
    }

    /**
     * Gives the account a personal VCS access token and an SSH key, i.e. two credentials that are on their own enough to
     * read and write the user's repositories.
     */
    private void giveUserCredentials() {
        user.setVcsAccessToken("vcs-token-" + user.getId());
        user.setVcsAccessTokenExpiryDate(ZonedDateTime.now().plusMonths(6));
        userRepository.save(user);

        UserSshPublicKey sshKey = new UserSshPublicKey();
        sshKey.setUserId(user.getId());
        sshKey.setLabel("Test key");
        sshKey.setPublicKey("ssh-ed25519 AAAA-not-a-real-key-" + user.getId());
        sshKey.setKeyHash("hash-" + user.getId());
        sshKey.setCreationDate(ZonedDateTime.now());
        userSshPublicKeyRepository.save(sshKey);
    }

    private User reloadUser() {
        return userRepository.getUserByLoginElseThrow(user.getLogin());
    }

    @Test
    void revokeAllCredentialsRemovesTheTokenAndTheSshKeys() {
        giveUserCredentials();

        accountCredentialRevocationService.revokeAllCredentials(user, "test");

        assertThat(reloadUser().getVcsAccessToken()).isNull();
        assertThat(reloadUser().getVcsAccessTokenExpiryDate()).isNull();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
    }

    @Test
    void aCompletedPasswordResetRevokesEverything() {
        // The remediation a user is told to perform has to end the intrusion, not just change one of several credentials.
        giveUserCredentials();
        user.setResetKey("reset-key-" + user.getId());
        user.setResetDate(Instant.now());
        userRepository.save(user);

        userService.completePasswordReset("new-Password-123", "reset-key-" + user.getId()).orElseThrow();

        assertThat(reloadUser().getVcsAccessToken()).isNull();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
    }

    @Test
    void deactivatingAUserRevokesEverything() {
        // Web login rejects a deactivated user, but the git paths accept a token or an SSH key without checking account
        // state, so deactivation is only effective once those are gone.
        giveUserCredentials();

        userCreationService.deactivateUser(user);

        assertThat(reloadUser().getActivated()).isFalse();
        assertThat(reloadUser().getVcsAccessToken()).isNull();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
    }

    @Test
    void softDeletingAUserRevokesEverything() {
        // The pre-existing cleanup here deleted the SSH keys but left the personal VCS access token behind, so a
        // soft-deleted account kept working over git.
        giveUserCredentials();

        userService.softDeleteUser(user.getLogin());

        User deleted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getVcsAccessToken()).isNull();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
    }

    @Test
    void changingThePasswordClearsTheTokenButKeepsTheSshKeys() {
        // Proportionality: the user proved control by entering the current password, so credentials they manage and can
        // see are left alone. The personal VCS access token is not one of those - it is a long-lived alternative password.
        giveUserCredentials();

        accountCredentialRevocationService.revokeVcsAccessToken(user, "test");

        assertThat(reloadUser().getVcsAccessToken()).isNull();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).hasSize(1);
    }

    @Test
    void revokingIsIdempotentAndSafeOnAnAccountWithoutCredentials() {
        // Runs on every password reset and deactivation, including for accounts that never had any of these credentials.
        accountCredentialRevocationService.revokeAllCredentials(user, "test");
        accountCredentialRevocationService.revokeAllCredentials(user, "test");

        assertThat(reloadUser().getVcsAccessToken()).isNull();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
    }
}
