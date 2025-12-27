package de.tum.cit.aet.artemis.core.security.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.PasskeyCredential;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

class ArtemisWebAuthnAuthenticationProviderTest {

    private WebAuthnRelyingPartyOperations relyingPartyOperations;

    private UserTestRepository userRepository;

    private PasskeyCredentialsRepository passkeyCredentialsRepository;

    private ArtemisWebAuthnAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        relyingPartyOperations = mock(WebAuthnRelyingPartyOperations.class);
        userRepository = mock(UserTestRepository.class);
        passkeyCredentialsRepository = mock(PasskeyCredentialsRepository.class);
        provider = new ArtemisWebAuthnAuthenticationProvider(relyingPartyOperations, userRepository, passkeyCredentialsRepository);
    }

    @Test
    void testConstructorThrowsExceptionForNullRelyingPartyOperations() {
        assertThatThrownBy(() -> new ArtemisWebAuthnAuthenticationProvider(null, userRepository, passkeyCredentialsRepository)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relyingPartyOperations cannot be null");
    }

    @Test
    void testConstructorThrowsExceptionForNullUserRepository() {
        assertThatThrownBy(() -> new ArtemisWebAuthnAuthenticationProvider(relyingPartyOperations, null, passkeyCredentialsRepository)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userRepository cannot be null");
    }

    @Test
    void testConstructorThrowsExceptionForNullPasskeyCredentialsRepository() {
        assertThatThrownBy(() -> new ArtemisWebAuthnAuthenticationProvider(relyingPartyOperations, userRepository, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passkeyCredentialsRepository cannot be null");
    }

    @Test
    void testSupportsWebAuthnAuthenticationRequestToken() {
        assertThat(provider.supports(WebAuthnAuthenticationRequestToken.class)).isTrue();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
        assertThat(provider.supports(Object.class)).isFalse();
    }

    @Test
    void testAuthenticateSuccess() {
        // Setup
        String credentialId = "test-credential-id";
        String username = "testuser";

        User user = new User();
        user.setLogin(username);
        user.setAuthorities(Set.of(new Authority(Role.STUDENT.getAuthority())));

        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);
        when(userEntity.getName()).thenReturn(username);

        WebAuthnAuthenticationRequestToken requestToken = createMockRequestToken(credentialId);
        when(relyingPartyOperations.authenticate(any())).thenReturn(userEntity);
        when(userRepository.findOneWithGroupsAndAuthoritiesByLogin(username)).thenReturn(Optional.of(user));
        when(passkeyCredentialsRepository.findByCredentialId(credentialId)).thenReturn(Optional.empty());

        // Execute
        var result = provider.authenticate(requestToken);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getDetails()).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        var details = (java.util.Map<String, Object>) result.getDetails();
        assertThat(details.get(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED)).isEqualTo(false);
    }

    @Test
    void testAuthenticateWithSuperAdminApprovedPasskey() {
        // Setup
        String credentialId = "test-credential-id";
        String username = "testuser";

        User user = new User();
        user.setLogin(username);
        user.setAuthorities(Set.of(new Authority(Role.ADMIN.getAuthority())));

        PasskeyCredential passkeyCredential = new PasskeyCredential();
        passkeyCredential.setCredentialId(credentialId);
        passkeyCredential.setSuperAdminApproved(true);

        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);
        when(userEntity.getName()).thenReturn(username);

        WebAuthnAuthenticationRequestToken requestToken = createMockRequestToken(credentialId);
        when(relyingPartyOperations.authenticate(any())).thenReturn(userEntity);
        when(userRepository.findOneWithGroupsAndAuthoritiesByLogin(username)).thenReturn(Optional.of(user));
        when(passkeyCredentialsRepository.findByCredentialId(credentialId)).thenReturn(Optional.of(passkeyCredential));

        // Execute
        var result = provider.authenticate(requestToken);

        // Verify
        assertThat(result).isNotNull();
        @SuppressWarnings("unchecked")
        var details = (java.util.Map<String, Object>) result.getDetails();
        assertThat(details.get(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED)).isEqualTo(true);
    }

    @Test
    void testAuthenticateUserNotFound() {
        // Setup
        String credentialId = "test-credential-id";
        String username = "nonexistent";

        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);
        when(userEntity.getName()).thenReturn(username);

        WebAuthnAuthenticationRequestToken requestToken = createMockRequestToken(credentialId);
        when(relyingPartyOperations.authenticate(any())).thenReturn(userEntity);
        when(userRepository.findOneWithGroupsAndAuthoritiesByLogin(username)).thenReturn(Optional.empty());

        // Execute & Verify
        assertThatThrownBy(() -> provider.authenticate(requestToken)).isInstanceOf(BadCredentialsException.class).hasMessageContaining("was not found in the database");
    }

    @Test
    void testAuthenticateRelyingPartyThrowsException() {
        // Setup
        String credentialId = "test-credential-id";

        WebAuthnAuthenticationRequestToken requestToken = createMockRequestToken(credentialId);
        when(relyingPartyOperations.authenticate(any())).thenThrow(new RuntimeException("WebAuthn verification failed"));

        // Execute & Verify
        assertThatThrownBy(() -> provider.authenticate(requestToken)).isInstanceOf(BadCredentialsException.class).hasMessageContaining("WebAuthn verification failed");
    }

    private WebAuthnAuthenticationRequestToken createMockRequestToken(String credentialId) {
        RelyingPartyAuthenticationRequest authRequest = mock(RelyingPartyAuthenticationRequest.class);
        when(authRequest.getPublicKey()).thenReturn(mock(org.springframework.security.web.webauthn.api.PublicKeyCredential.class));
        when(authRequest.getPublicKey().getId()).thenReturn(credentialId);

        return new WebAuthnAuthenticationRequestToken(authRequest);
    }
}
