package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.server.session.ServerSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;

/**
 * Unit tests for authenticating a git operation over SSH by public key.
 * <p>
 * An SSH key is a credential of its own: nothing else on this path consults the account, so whatever this method accepts
 * gets read and write access to the user's repositories. The interesting cases are therefore the ones where a key exists
 * but must still be refused - because it expired, because it does not match, or because the account behind it is no
 * longer allowed to log in at all.
 */
@ExtendWith(MockitoExtension.class)
class GitPublickeyAuthenticatorServiceTest {

    private static final long USER_ID = 42L;

    private static KeyPair keyPair;

    private static KeyPair otherKeyPair;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private UserSshPublicKeyRepository userSshPublicKeyRepository;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private BuildAgentNetworkPolicy buildAgentNetworkPolicy;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @Mock
    private BuildAgentAddressRegistryService buildAgentAddressRegistryService;

    @Mock
    private ServerSession session;

    private GitPublickeyAuthenticatorService authenticatorService;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        authenticatorService = new GitPublickeyAuthenticatorService(userRepository, Optional.of(distributedDataAccessService), userSshPublicKeyRepository, rateLimitService,
                buildAgentNetworkPolicy, Optional.of(buildAgentAddressRegistryService));
        // Rate limiting is keyed by the client address, so the session has to report one for every path that reaches it.
        lenient().when(session.getClientAddress()).thenReturn(new InetSocketAddress("192.0.2.10", 52000));
    }

    private static UserSshPublicKey storedKey(PublicKey publicKey, ZonedDateTime expiryDate) {
        UserSshPublicKey storedKey = new UserSshPublicKey();
        storedKey.setUserId(USER_ID);
        storedKey.setLabel("laptop");
        storedKey.setPublicKey(PublicKeyEntry.toString(publicKey));
        storedKey.setExpiryDate(expiryDate);
        return storedKey;
    }

    private static User user(boolean activated, boolean deleted) {
        User user = new User();
        user.setId(USER_ID);
        user.setLogin("ge12abc");
        user.setActivated(activated);
        user.setDeleted(deleted);
        return user;
    }

    private void withStoredKey(UserSshPublicKey storedKey) {
        when(userSshPublicKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(storedKey));
    }

    @Test
    void authenticate_withAKeyThatMatchesAnActiveAccount_succeedsAndRemembersTheUser() {
        withStoredKey(storedKey(keyPair.getPublic(), null));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true, false)));

        boolean authenticated = authenticatorService.authenticate("ge12abc", keyPair.getPublic(), session);

        assertThat(authenticated).isTrue();
        // Everything downstream reads the user off the session, so authenticating without setting it would let the request through as nobody.
        verify(session).setAttribute(eq(SshConstants.USER_KEY), any(User.class));
        verify(session).setAttribute(SshConstants.IS_BUILD_AGENT_KEY, false);
    }

    @Test
    void authenticate_withAKeyThatHasNotExpiredYet_succeeds() {
        withStoredKey(storedKey(keyPair.getPublic(), ZonedDateTime.now().plusDays(1)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true, false)));

        assertThat(authenticatorService.authenticate("ge12abc", keyPair.getPublic(), session)).isTrue();
    }

    @Test
    void authenticate_withAnExpiredKey_isRefusedAndTheSessionIsTold() throws Exception {
        // A key that expired has to stop working, and the user has to learn why rather than seeing an unexplained refusal.
        withStoredKey(storedKey(keyPair.getPublic(), ZonedDateTime.now().minusDays(1)));

        assertThat(authenticatorService.authenticate("ge12abc", keyPair.getPublic(), session)).isFalse();
        verify(session).disconnect(anyInt(), contains("expired"));
        verify(userRepository, never()).findById(any());
    }

    @Test
    void authenticate_withAKeyOfADeactivatedAccount_isRefused() {
        // Nothing else on this path consults account state, so without this check a deactivated user would keep access through a key issued earlier.
        withStoredKey(storedKey(keyPair.getPublic(), null));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(false, false)));

        assertThat(authenticatorService.authenticate("ge12abc", keyPair.getPublic(), session)).isFalse();
        verify(session, never()).setAttribute(eq(SshConstants.USER_KEY), any(User.class));
    }

    @Test
    void authenticate_withAKeyOfADeletedAccount_isRefused() {
        withStoredKey(storedKey(keyPair.getPublic(), null));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true, true)));

        assertThat(authenticatorService.authenticate("ge12abc", keyPair.getPublic(), session)).isFalse();
        verify(session, never()).setAttribute(eq(SshConstants.USER_KEY), any(User.class));
    }

    @Test
    void authenticate_withAKeyWhoseOwnerNoLongerExists_isRefused() {
        withStoredKey(storedKey(keyPair.getPublic(), null));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThat(authenticatorService.authenticate("ge12abc", keyPair.getPublic(), session)).isFalse();
    }

    @Test
    void authenticate_whenTheStoredKeyDoesNotMatchTheOfferedOne_isRefused() {
        // The lookup is by fingerprint, so a collision or a tampered record must not be enough on its own: the keys themselves have to match.
        withStoredKey(storedKey(keyPair.getPublic(), null));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(true, false)));

        assertThat(authenticatorService.authenticate("ge12abc", otherKeyPair.getPublic(), session)).isFalse();
        verify(session, never()).setAttribute(eq(SshConstants.USER_KEY), any(User.class));
    }

    @Test
    void authenticate_whenTheClientIsRateLimited_isRefusedWithoutReadingTheAccount() {
        // The rate limit is what makes guessing keys expensive, so it has to be enforced before any account lookup.
        withStoredKey(storedKey(keyPair.getPublic(), null));
        doThrow(new RuntimeException("too many attempts")).when(rateLimitService).enforcePerMinute(any(), any());

        assertThat(authenticatorService.authenticate("ge12abc", keyPair.getPublic(), session)).isFalse();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void authenticate_withAKeyNobodyRegistered_fallsThroughToTheBuildAgents() {
        // An unknown key is not necessarily a user's: build agents authenticate the same way, with keys that are not stored per user.
        when(userSshPublicKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.empty());
        when(distributedDataAccessService.getBuildAgentInformation()).thenReturn(List.of());

        assertThat(authenticatorService.authenticate("buildagent", keyPair.getPublic(), session)).isFalse();
        verify(distributedDataAccessService).getBuildAgentInformation();
    }
}
