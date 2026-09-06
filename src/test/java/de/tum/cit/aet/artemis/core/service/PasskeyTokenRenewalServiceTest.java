package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;

/**
 * Unit tests for the decision that ends a silently rotating session.
 * <p>
 * Every path here is a way for a session to outlive what authorised it, and the failure mode is the same in all of them:
 * the session keeps being extended, which is invisible until someone wonders why a deleted passkey or a deactivated
 * account still has access. The decision is therefore tested directly rather than only through the filter.
 */
@ExtendWith(MockitoExtension.class)
class PasskeyTokenRenewalServiceTest {

    private static final String LOGIN = "student1";

    private static final long USER_ID = 42L;

    private static final String CREDENTIAL_ID = "credential-1";

    @Mock
    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private UserTestRepository userRepository;

    /**
     * @param passkeysEnabled whether passkey support is on; when it is off the repository bean does not exist, because it
     *                            is {@code @Conditional(PasskeyEnabled.class)}
     * @return the service under test
     */
    private PasskeyTokenRenewalService serviceWithPasskeys(boolean passkeysEnabled) {
        return new PasskeyTokenRenewalService(passkeysEnabled ? Optional.of(passkeyCredentialsRepository) : Optional.empty(), userRepository, userActivityService);
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setLogin(LOGIN);
        user.setActivated(true);
        user.setDeleted(false);
        return user;
    }

    @Test
    void anExistingPasskeyKeepsTheSessionExtendable() {
        when(passkeyCredentialsRepository.findByCredentialId(CREDENTIAL_ID)).thenReturn(Optional.of(new PasskeyCredential()));

        assertThat(serviceWithPasskeys(true).mayExtendPasskeySession(CREDENTIAL_ID)).isTrue();
    }

    @Test
    void aDeletedPasskeyEndsTheSession() {
        when(passkeyCredentialsRepository.findByCredentialId(CREDENTIAL_ID)).thenReturn(Optional.empty());

        assertThat(serviceWithPasskeys(true).mayExtendPasskeySession(CREDENTIAL_ID)).isFalse();
    }

    /**
     * Tokens issued before the credential-id claim existed have to keep working, otherwise deploying the check logs every
     * passkey user out at once.
     */
    @Test
    void aTokenWithoutACredentialIdIsStillExtendedWhilePasskeysAreEnabled() {
        assertThat(serviceWithPasskeys(true).mayExtendPasskeySession(null)).isTrue();
    }

    /**
     * Disabling passkeys has to end the passkey sessions it leaves behind, including the pre-claim ones: those carry no
     * credential id, so checking that first would have kept extending them for the full passkey lifetime after passkey
     * support was turned off.
     */
    @Test
    void noPasskeySessionIsExtendedOncePasskeysAreDisabled() {
        PasskeyTokenRenewalService service = serviceWithPasskeys(false);

        assertThat(service.mayExtendPasskeySession(CREDENTIAL_ID)).isFalse();
        assertThat(service.mayExtendPasskeySession(null)).isFalse();
    }

    @Test
    void anActiveAccountKeepsItsSessionExtendable() {
        when(userRepository.findOneByLogin(LOGIN)).thenReturn(Optional.of(activeUser()));

        assertThat(serviceWithPasskeys(true).mayExtendSessionForAccount(LOGIN, Instant.now())).isTrue();
    }

    @Test
    void aDeactivatedAccountEndsItsSession() {
        User user = activeUser();
        user.setActivated(false);
        when(userRepository.findOneByLogin(LOGIN)).thenReturn(Optional.of(user));

        assertThat(serviceWithPasskeys(true).mayExtendSessionForAccount(LOGIN, Instant.now())).isFalse();
    }

    @Test
    void aSoftDeletedAccountEndsItsSession() {
        User user = activeUser();
        user.setDeleted(true);
        when(userRepository.findOneByLogin(LOGIN)).thenReturn(Optional.of(user));

        assertThat(serviceWithPasskeys(true).mayExtendSessionForAccount(LOGIN, Instant.now())).isFalse();
    }

    @Test
    void anAccountThatNoLongerExistsEndsItsSession() {
        when(userRepository.findOneByLogin(LOGIN)).thenReturn(Optional.empty());

        assertThat(serviceWithPasskeys(true).mayExtendSessionForAccount(LOGIN, Instant.now())).isFalse();
    }

    /**
     * A session established before the credentials changed is what a password reset has to end, since the reset cannot
     * reach the token that was already issued.
     */
    @Test
    void aSessionOlderThanTheCredentialChangeEndsThere() {
        User user = activeUser();
        Instant credentialsChanged = Instant.now();
        // The timestamp lives in user_activity now, so it is stubbed on the service that owns it rather than set on the user.
        lenient().when(userActivityService.findCredentialsChangedDate(USER_ID)).thenReturn(credentialsChanged);
        lenient().when(userRepository.findOneByLogin(LOGIN)).thenReturn(Optional.of(user));
        PasskeyTokenRenewalService service = serviceWithPasskeys(true);

        assertThat(service.mayExtendSessionForAccount(LOGIN, credentialsChanged.minusSeconds(1))).isFalse();
        assertThat(service.mayExtendSessionForAccount(LOGIN, credentialsChanged.plusSeconds(1))).isTrue();
    }
}
