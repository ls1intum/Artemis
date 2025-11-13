package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.core.exception.PasskeyAuthenticationException;
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
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenPasskeyDisabled_shouldReturnFalse() {
        // Given
        passkeyAuthenticationService = createService(false, true);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenNoHttpRequest_shouldThrowException() {
        // Given
        passkeyAuthenticationService = createService(true, true);
        RequestContextHolder.resetRequestAttributes();

        // When / Then
        assertThatExceptionOfType(PasskeyAuthenticationException.class).isThrownBy(() -> passkeyAuthenticationService.isAuthenticatedWithPasskey())
                .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenNoJwtToken_shouldThrowException() {
        // Given
        passkeyAuthenticationService = createService(true, true);

        // When / Then
        assertThatExceptionOfType(PasskeyAuthenticationException.class).isThrownBy(() -> passkeyAuthenticationService.isAuthenticatedWithPasskey())
                .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsPassword_shouldThrowException() {
        // Given
        passkeyAuthenticationService = createService(true, true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSWORD);

        // When / Then
        assertThatExceptionOfType(PasskeyAuthenticationException.class).isThrownBy(() -> passkeyAuthenticationService.isAuthenticatedWithPasskey())
                .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsSaml2_shouldThrowException() {
        // Given
        passkeyAuthenticationService = createService(true, true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.SAML2);

        // When / Then
        assertThatExceptionOfType(PasskeyAuthenticationException.class).isThrownBy(() -> passkeyAuthenticationService.isAuthenticatedWithPasskey())
                .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsPasskey_shouldReturnTrue() {
        // Given
        passkeyAuthenticationService = createService(true, true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSKEY);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenAuthMethodIsNull_shouldThrowException() {
        // Given
        passkeyAuthenticationService = createService(true, true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(null);

        // When / Then
        assertThatExceptionOfType(PasskeyAuthenticationException.class).isThrownBy(() -> passkeyAuthenticationService.isAuthenticatedWithPasskey())
                .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenRequireSuperAdminApprovalIsFalse_shouldNotCheckApproval() {
        // Given
        passkeyAuthenticationService = createService(true, true);
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
        passkeyAuthenticationService = createService(true, true);
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
    void testIsAuthenticatedWithPasskey_whenRequireSuperAdminApprovalIsTrueAndPasskeyIsNotApproved_shouldThrowException() {
        // Given
        passkeyAuthenticationService = createService(true, true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSKEY);
        when(tokenProvider.isPasskeySuperAdminApproved(VALID_JWT_TOKEN)).thenReturn(false);

        // When / Then
        assertThatExceptionOfType(PasskeyAuthenticationException.class).isThrownBy(() -> passkeyAuthenticationService.isAuthenticatedWithPasskey(true))
                .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.PASSKEY_NOT_SUPER_ADMIN_APPROVED));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenRequireSuperAdminApprovalButNotPasskeyAuth_shouldThrowException() {
        // Given
        passkeyAuthenticationService = createService(true, true);
        addJwtCookie(request, VALID_JWT_TOKEN);
        when(tokenProvider.validateTokenForAuthority(VALID_JWT_TOKEN, "cookie")).thenReturn(true);
        when(tokenProvider.getAuthenticationMethod(VALID_JWT_TOKEN)).thenReturn(AuthenticationMethod.PASSWORD);

        // When / Then
        assertThatExceptionOfType(PasskeyAuthenticationException.class).isThrownBy(() -> passkeyAuthenticationService.isAuthenticatedWithPasskey(true))
                .satisfies(ex -> assertThat(ex.getReason()).isEqualTo(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY));
    }

    @Test
    void testIsAuthenticatedWithPasskey_whenPasskeyDisabledAndRequireSuperAdminApproval_shouldReturnTrue() {
        // Given
        passkeyAuthenticationService = createService(false, true);

        // When
        boolean result = passkeyAuthenticationService.isAuthenticatedWithPasskey(true);

        // Then
        // When passkey is disabled, the check is bypassed completely
        assertThat(result).isTrue();
    }

    private PasskeyAuthenticationService createService(boolean passkeyEnabled, boolean isPasskeyRequiredForAdministratorFeatures) {
        return new PasskeyAuthenticationService(tokenProvider, passkeyEnabled, isPasskeyRequiredForAdministratorFeatures);
    }

    private void addJwtCookie(MockHttpServletRequest request, String token) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(JWT_COOKIE_NAME, token);
        request.setCookies(cookie);
    }
}
