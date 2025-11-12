package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

@ExtendWith(MockitoExtension.class)
class PasskeyAuthenticationServiceTest {

    @Mock
    private TokenProvider tokenProvider;

    private PasskeyAuthenticationService passkeyAuthenticationService;

    private MockHttpServletRequest request;

    private static final String VALID_JWT_TOKEN = "valid.jwt.token";

    private static final String JWT_COOKIE_NAME = "jwt";

    @BeforeEach
    void setUp() {
        passkeyAuthenticationService = new PasskeyAuthenticationService(tokenProvider);
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenPasskeyDisabled_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", false);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenNoHttpRequest_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        RequestContextHolder.resetRequestAttributes();

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenNoJwtToken_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsPassword_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSWORD);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsSaml2_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.SAML2);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsPasskey_shouldReturnTrue() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSKEY);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsNull_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(null);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenRequireSuperAdminApprovalIsFalse_shouldNotCheckApproval() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSKEY);
        // Passkey approval check is not performed when requireSuperAdminApproval is false

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey(false);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenRequireSuperAdminApprovalIsTrueAndPasskeyIsApproved_shouldReturnTrue() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSKEY);
        when(tokenProvider.isPasskeySuperAdminApproved(VALID_JWT_TOKEN)).thenReturn(true);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey(true);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenRequireSuperAdminApprovalIsTrueAndPasskeyIsNotApproved_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSKEY);
        when(tokenProvider.isPasskeySuperAdminApproved(VALID_JWT_TOKEN)).thenReturn(false);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey(true);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenRequireSuperAdminApprovalButNotPasskeyAuth_shouldReturnFalse() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSWORD);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey(true);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenPasskeyDisabledAndRequireSuperAdminApproval_shouldReturnTrue() {
        // Given
        ReflectionTestUtils.setField(passkeyAuthenticationService, "passkeyEnabled", false);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey(true);

        // Then
        // When passkey is disabled, the check is bypassed completely
        assertThat(result).isTrue();
    }

    private void addJwtCookie(MockHttpServletRequest request, String token) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(JWT_COOKIE_NAME, token);
        request.setCookies(cookie);
    }
}
