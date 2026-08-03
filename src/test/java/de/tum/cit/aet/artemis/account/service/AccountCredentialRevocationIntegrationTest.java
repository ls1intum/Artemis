package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.account.util.PasskeyCredentialUtilService;
import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.dto.CredentialRevocationChoiceDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies against a real database that the account lifecycle transitions actually revoke the credentials that can be
 * used instead of the password.
 * <p>
 * These are the cases where a silent gap is expensive: a user who resets their password because they suspect a
 * compromise, and an administrator who deactivates an account. Each transition is asserted per credential type - the
 * passkeys, the personal VCS access token together with its expiry date, and the SSH keys - because the failure mode is
 * one type being forgotten, which is how passkeys and the personal VCS access token came to be missing from the
 * soft-delete cleanup in the first place.
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
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Autowired
    private PasskeyCredentialUtilService passkeyCredentialUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private User user;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
    }

    /**
     * Gives the account one of each credential this service revokes: a passkey, a personal VCS access token and an SSH
     * key. The latter two are on their own enough to read and write the user's repositories, and the passkey is on its
     * own enough to log in.
     */
    private void giveUserCredentials() {
        // Cleared first: the fixture user is reused across the tests in this class, so a test that deliberately leaves a
        // credential in place would otherwise make a later test see two of them.
        passkeyCredentialsRepository.deleteAllByUserId(user.getId());
        userSshPublicKeyRepository.deleteAll(userSshPublicKeyRepository.findAllByUserId(user.getId()));

        passkeyCredentialUtilService.createAndSavePasskeyCredential(user);

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

    /**
     * Asserts that the personal VCS access token is gone, expiry date included: clearing only the token would leave a
     * dangling expiry date, and a later change that forgot the token itself would still look like a revocation.
     */
    private void assertVcsAccessTokenRevoked() {
        assertVcsAccessTokenRevoked(reloadUser());
    }

    /**
     * @param reloaded the account as it was read back from the database; a soft-deleted account has to be loaded by id,
     *                     because it can no longer be looked up by login
     */
    private void assertVcsAccessTokenRevoked(User reloaded) {
        assertThat(reloaded.getVcsAccessToken()).isNull();
        assertThat(reloaded.getVcsAccessTokenExpiryDate()).isNull();
    }

    private void assertVcsAccessTokenKept() {
        User reloaded = reloadUser();
        assertThat(reloaded.getVcsAccessToken()).isEqualTo("vcs-token-" + user.getId());
        assertThat(reloaded.getVcsAccessTokenExpiryDate()).isNotNull();
    }

    @Test
    void revokeAllCredentialsRemovesEveryCredentialType() {
        giveUserCredentials();

        accountCredentialRevocationService.revokeAllCredentials(user, "test");

        assertVcsAccessTokenRevoked();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }

    @Test
    void aCompletedPasswordResetRevokesEverything() {
        // The remediation a user is told to perform has to end the intrusion, not just change one of several credentials.
        giveUserCredentials();
        user.setResetKey("reset-key-" + user.getId());
        user.setResetDate(Instant.now());
        userRepository.save(user);

        userService.completePasswordReset("new-Password-123", "reset-key-" + user.getId()).orElseThrow();

        assertVcsAccessTokenRevoked();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }

    @Test
    void deactivatingAUserRevokesEverything() {
        // Web login rejects a deactivated user, but the git paths accept a token or an SSH key without checking account
        // state, so deactivation is only effective once those are gone.
        giveUserCredentials();

        userCreationService.deactivateUser(user);

        assertThat(reloadUser().getActivated()).isFalse();
        assertVcsAccessTokenRevoked();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }

    @Test
    void softDeletingAUserRevokesEverything() {
        // The pre-existing cleanup here deleted the SSH keys but left the personal VCS access token behind, so a
        // soft-deleted account kept working over git.
        giveUserCredentials();

        userService.softDeleteUser(user.getLogin());

        User deleted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertVcsAccessTokenRevoked(deleted);
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }

    /**
     * Goes through {@link UserService#changePassword} rather than the revocation service directly, because the wiring is
     * the part that can break: a change that dropped the revocation call from the password-change flow would still leave
     * a direct call to the service passing.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void aPasswordChangeRevokesOnlyWhatTheUserSelected() {
        // The user decides, because only they know whether the old password may have been seen by someone else. Selecting
        // the tokens must not take away the SSH keys they use from their machines.
        giveUserCredentials();

        userService.changePassword(UserFactory.USER_PASSWORD, "new-Password-123", new CredentialRevocationChoiceDTO(false, false, true));

        assertVcsAccessTokenRevoked();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).hasSize(1);
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void aPasswordChangeCanRevokeOnlyTheSshKeys() {
        giveUserCredentials();

        userService.changePassword(UserFactory.USER_PASSWORD, "new-Password-123", new CredentialRevocationChoiceDTO(false, true, false));

        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertVcsAccessTokenKept();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void aRoutinePasswordChangeRevokesNothing() {
        // The default when the request expresses no choice: a routine rotation should not cost the user their
        // authenticators, keys and tokens.
        giveUserCredentials();

        userService.changePassword(UserFactory.USER_PASSWORD, "new-Password-123", CredentialRevocationChoiceDTO.none());

        assertVcsAccessTokenKept();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).hasSize(1);
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).hasSize(1);
    }

    /**
     * Deactivation through the admin edit form has to revoke the same credentials as the dedicated deactivate endpoint:
     * {@code updateUser} writes the {@code activated} flag itself, so without this an account the administrator sees as
     * deactivated keeps working over git and keeps its passkeys.
     */
    @Test
    void deactivatingAUserThroughTheAdminUpdateRevokesEverything() {
        giveUserCredentials();

        // Loaded with authorities, because updateUser reconciles them and the admin resource loads the user the same way.
        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setActivated(false);
        update.setPassword(null);
        userCreationService.updateUser(userWithAuthorities, update);

        assertThat(reloadUser().getActivated()).isFalse();
        assertVcsAccessTokenRevoked();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }

    /**
     * The mirror image: an unrelated edit must not cost the user their credentials, otherwise every admin who fixes a
     * typo in a name logs that user out of their repositories.
     */
    @Test
    void anUnrelatedAdminUpdateKeepsTheCredentials() {
        giveUserCredentials();

        User userWithAuthorities = userRepository.findOneWithAuthoritiesByLogin(user.getLogin()).orElseThrow();
        ManagedUserVM update = new ManagedUserVM(userWithAuthorities);
        update.setPassword(null);
        update.setFirstName("Renamed");
        userCreationService.updateUser(userWithAuthorities, update);

        assertVcsAccessTokenKept();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).hasSize(1);
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).hasSize(1);
    }

    @Test
    void revokingIsIdempotentAndSafeOnAnAccountWithoutCredentials() {
        // Runs on every password reset and deactivation, including for accounts that never had any of these credentials.
        accountCredentialRevocationService.revokeAllCredentials(user, "test");
        accountCredentialRevocationService.revokeAllCredentials(user, "test");

        assertVcsAccessTokenRevoked();
        assertThat(userSshPublicKeyRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(passkeyCredentialsRepository.findByUser(user.getId())).isEmpty();
    }
}
