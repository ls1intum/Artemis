package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.management.SecurityMetersService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.PasskeyTokenRenewalService;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class JWTFilterTest {

    private static final long TOKEN_VALIDITY_IN_MILLISECONDS = 60000; // 60 seconds

    /** Generous next to the 60 second token validity, so only the tests that mean to hit the ceiling reach it. */
    private static final long MAX_SESSION_LIFETIME_IN_SECONDS = 3600;

    private TokenProvider tokenProvider;

    private JWTFilter jwtFilter;

    private PasskeyTokenRenewalService passkeyTokenRenewalService;

    private JWTCookieService jwtCookieServiceMock;

    @BeforeEach
    void setup() {
        ArtemisProperties jHipsterProperties = new ArtemisProperties();
        String base64Secret = "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setBase64Secret(base64Secret);

        SecurityMetersService securityMetersService = new SecurityMetersService(new SimpleMeterRegistry());

        tokenProvider = new TokenProvider(jHipsterProperties, securityMetersService);
        ReflectionTestUtils.setField(tokenProvider, "key", Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret)));
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", TOKEN_VALIDITY_IN_MILLISECONDS);
        // Silent rotation compares the remaining lifetime against half of the remember-me validity, so it has to be set
        // for any rotation to be due at all.
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMillisecondsForRememberMe", TOKEN_VALIDITY_IN_MILLISECONDS);

        jwtCookieServiceMock = mock(JWTCookieService.class);
        JWTCookieService jwtCookieService = jwtCookieServiceMock;
        // The filter calls toString() on whatever the service returns, so a real cookie is needed rather than null.
        when(jwtCookieService.buildRotatedCookie(any(), anyLong())).thenReturn(ResponseCookie.from(Constants.JWT_COOKIE_NAME, "rotated").build());

        passkeyTokenRenewalService = mock(PasskeyTokenRenewalService.class);
        // Default to "the passkey still exists", so the existing rotation tests keep exercising rotation itself.
        when(passkeyTokenRenewalService.mayExtendPasskeySession(any())).thenReturn(true);
        when(passkeyTokenRenewalService.mayExtendSessionForAccount(any(), any())).thenReturn(true);

        jwtFilter = new JWTFilter(tokenProvider, jwtCookieService, 15552000, passkeyTokenRenewalService, MAX_SESSION_LIFETIME_IN_SECONDS);
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    @Test
    void testJWTFilterCookie() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        String jwt = tokenProvider.createToken(authentication, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.JWT_COOKIE_NAME, jwt));
        request.setRequestURI("/api/core/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        jwtFilter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("test-user");
    }

    @Test
    void testJWTFilterCookieAndBearer() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));

        String jwt = tokenProvider.createToken(authentication, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.JWT_COOKIE_NAME, jwt));
        request.addHeader(HttpHeaders.AUTHORIZATION, Constants.BEARER_PREFIX + jwt);
        request.setRequestURI("/api/core/test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        jwtFilter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void testJWTFilterBearer() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));

        String jwt = tokenProvider.createToken(authentication, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, Constants.BEARER_PREFIX + jwt);
        request.setRequestURI("/api/core/test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        jwtFilter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("test-user");
    }

    @Test
    void testJWTFilterInvalidToken() throws Exception {
        String jwt = "wrong_jwt";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.JWT_COOKIE_NAME, jwt));
        request.setRequestURI("/api/core/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        jwtFilter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void testJWTFilterMissingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/core/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        jwtFilter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /**
     * Builds a passkey token that is already past half its lifetime, i.e. one a rotation is due for.
     */
    private String createRotationDuePasskeyToken(String credentialId) {
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Map<String, Object> details = new HashMap<>();
        details.put(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, false);
        if (credentialId != null) {
            details.put(TokenProvider.PASSKEY_CREDENTIAL_ID, credentialId);
        }
        authentication.setDetails(details);

        Date issuedAt = new Date(System.currentTimeMillis() - TOKEN_VALIDITY_IN_MILLISECONDS);
        Date expiration = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS / 10);
        return tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
    }

    private MockHttpServletResponse filterWithToken(String jwt) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(Constants.JWT_COOKIE_NAME, jwt));
        request.setRequestURI("/api/core/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        jwtFilter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void aPasskeySessionIsExtendedWhileItsPasskeyStillExists() throws Exception {
        when(passkeyTokenRenewalService.mayExtendPasskeySession("credential-1")).thenReturn(true);

        MockHttpServletResponse response = filterWithToken(createRotationDuePasskeyToken("credential-1"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).as("the session is extended").isNotNull();
    }

    @Test
    void aPasskeySessionIsNotExtendedOnceItsPasskeyIsGone() throws Exception {
        // Deleting a passkey - the remediation after a compromise - has to stop the sessions it created from being
        // extended, otherwise the credential is gone but the session it minted lives on for months.
        when(passkeyTokenRenewalService.mayExtendPasskeySession("credential-1")).thenReturn(false);

        MockHttpServletResponse response = filterWithToken(createRotationDuePasskeyToken("credential-1"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).as("the session is not extended").isNull();
    }

    @Test
    void aRotatedTokenKeepsTheCredentialIdSoLaterRotationsCanStillCheckIt() throws Exception {
        // Without this the check works exactly once: the authentication is rebuilt from the expiring token and carries no
        // details, so the rotated token would lose the claim, and every later rotation would look like a legacy token and
        // be extended unconditionally.
        when(passkeyTokenRenewalService.mayExtendPasskeySession("credential-1")).thenReturn(true);
        ArgumentCaptor<String> rotatedToken = ArgumentCaptor.forClass(String.class);

        filterWithToken(createRotationDuePasskeyToken("credential-1"));

        verify(jwtCookieServiceMock).buildRotatedCookie(rotatedToken.capture(), anyLong());
        assertThat(tokenProvider.getPasskeyCredentialId(rotatedToken.getValue())).isEqualTo("credential-1");
    }

    @Test
    void aRotatedTokenKeepsTheSuperAdminApprovalFlag() {
        // Same root cause: deriving the passkey claims from the rebuilt authentication silently reset this flag on every
        // rotation, quietly downgrading an approved super admin.
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Map<String, Object> details = new HashMap<>();
        details.put(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, true);
        details.put(TokenProvider.PASSKEY_CREDENTIAL_ID, "credential-1");
        authentication.setDetails(details);
        String original = tokenProvider.createToken(authentication, new Date(), new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS), null, true);

        var rebuiltAuthentication = tokenProvider.getAuthentication(original);
        String rotated = tokenProvider.createToken(rebuiltAuthentication, new Date(), new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS), null, true,
                tokenProvider.getPasskeyCredentialId(original), tokenProvider.isPasskeySuperAdminApproved(original));

        assertThat(tokenProvider.isPasskeySuperAdminApproved(rotated)).isTrue();
        assertThat(tokenProvider.getPasskeyCredentialId(rotated)).isEqualTo("credential-1");
    }

    /**
     * Builds a "remember me" password session token that a rotation is due for.
     */
    private String createRotationDueRememberMeToken() {
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Date issuedAt = new Date(System.currentTimeMillis() - TOKEN_VALIDITY_IN_MILLISECONDS);
        Date expiration = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS / 10);
        return tokenProvider.createToken(authentication, issuedAt, expiration, null, false, null, false, true);
    }

    @Test
    void anActiveRememberMeSessionIsExtended() throws Exception {
        // Password sessions were never extended before, which is what makes shortening their validity acceptable: an active
        // user does not notice, and each extension is a checkpoint where the account is re-examined.
        MockHttpServletResponse response = filterWithToken(createRotationDueRememberMeToken());

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNotNull();
    }

    @Test
    void aRememberMeSessionIsNotExtendedBeyondTheCeiling() throws Exception {
        // The ceiling is the one bound on how long an active session can be kept alive without re-authenticating, and it
        // is measured from the login, so it does not depend on when the rotating requests happen to arrive.
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Date issuedAt = new Date(System.currentTimeMillis() - Math.multiplyExact(MAX_SESSION_LIFETIME_IN_SECONDS, 1000));
        Date expiration = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS / 10);
        String tokenAtTheCeiling = tokenProvider.createToken(authentication, issuedAt, expiration, null, false, null, false, true);

        MockHttpServletResponse response = filterWithToken(tokenAtTheCeiling);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void aRefusedRenewalIsNotLookedUpAgainForTheSameToken() throws Exception {
        // A refusal leaves the cookie untouched, so the token stays rotation-due for the rest of its life. Without
        // remembering the decision, every later request would repeat these lookups - for a session refused early in its
        // second half, that is per-request database load for as long as the token lives.
        when(passkeyTokenRenewalService.mayExtendSessionForAccount(any(), any())).thenReturn(false);
        String token = createRotationDueRememberMeToken();

        for (int request = 0; request < 5; request++) {
            assertThat(filterWithToken(token).getHeader(HttpHeaders.SET_COOKIE)).as("a refused session is never extended").isNull();
        }

        verify(passkeyTokenRenewalService, times(1)).mayExtendSessionForAccount(any(), any());
    }

    @Test
    void aRefusedRenewalDoesNotSuppressTheCheckForADifferentToken() throws Exception {
        // The decision is remembered per token, not per account: a second session of the same user has its own token and
        // has to be judged on its own, otherwise one refusal would stop every other session from being extended.
        when(passkeyTokenRenewalService.mayExtendSessionForAccount(any(), any())).thenReturn(false);
        filterWithToken(createRotationDueRememberMeToken());

        when(passkeyTokenRenewalService.mayExtendSessionForAccount(any(), any())).thenReturn(true);
        // A second token for the same account, issued two seconds earlier. The gap has to be whole seconds: `iat` is
        // serialised with second precision, so a millisecond apart would produce the very same token and digest.
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Date issuedAt = new Date(System.currentTimeMillis() - TOKEN_VALIDITY_IN_MILLISECONDS - 2000);
        Date expiration = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS / 10);
        String otherToken = tokenProvider.createToken(authentication, issuedAt, expiration, null, false, null, false, true);

        assertThat(filterWithToken(otherToken).getHeader(HttpHeaders.SET_COOKIE)).as("a different session is judged on its own").isNotNull();
    }

    @Test
    void aRememberMeSessionIsNotExtendedBeyondItsLifetimeCeiling() throws Exception {
        // The extension count alone would let an old session keep taking fresh windows. That matters most for an externally
        // managed account, where a password reset or a deactivation performed in the directory leaves nothing for the other
        // renewal checks to read, so this ceiling is the only thing that ends the session.
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Date issuedAt = new Date(System.currentTimeMillis() - Math.multiplyExact(MAX_SESSION_LIFETIME_IN_SECONDS, 1000));
        Date expiration = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS / 10);
        String token = tokenProvider.createToken(authentication, issuedAt, expiration, null, false, null, false, true);

        MockHttpServletResponse response = filterWithToken(token);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void aRememberMeExtensionNeverReachesPastTheLifetimeCeiling() throws Exception {
        // Just inside the ceiling, so a window is still granted - but a clipped one, not another full validity period.
        ArgumentCaptor<String> rotated = ArgumentCaptor.forClass(String.class);
        long remainingLifetimeInMs = TOKEN_VALIDITY_IN_MILLISECONDS / 2;
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Date issuedAt = new Date(System.currentTimeMillis() - Math.multiplyExact(MAX_SESSION_LIFETIME_IN_SECONDS, 1000) + remainingLifetimeInMs);
        Date expiration = new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS / 10);
        String token = tokenProvider.createToken(authentication, issuedAt, expiration, null, false, null, false, true);

        MockHttpServletResponse response = filterWithToken(token);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNotNull();
        verify(jwtCookieServiceMock).buildRotatedCookie(rotated.capture(), anyLong());
        Date ceiling = new Date(issuedAt.getTime() + Math.multiplyExact(MAX_SESSION_LIFETIME_IN_SECONDS, 1000));
        assertThat(tokenProvider.getExpirationDate(rotated.getValue())).isBeforeOrEqualTo(ceiling);
    }

    @Test
    void anExtensionKeepsTheSessionEligibleForTheNextOne() throws Exception {
        // The rotated token has to carry the "remember me" claim forward: it is what makes a session extendable at all, so
        // dropping it would silently make every extension the last one.
        ArgumentCaptor<String> rotated = ArgumentCaptor.forClass(String.class);

        filterWithToken(createRotationDueRememberMeToken());

        verify(jwtCookieServiceMock).buildRotatedCookie(rotated.capture(), anyLong());
        assertThat(tokenProvider.isRememberMeSession(rotated.getValue())).isTrue();
    }

    @Test
    void aPlainSessionIsNeverExtended() throws Exception {
        // Without "remember me" the session is meant to be short; extending it would quietly change that.
        var authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        Date issuedAt = new Date(System.currentTimeMillis() - TOKEN_VALIDITY_IN_MILLISECONDS);
        String jwt = tokenProvider.createToken(authentication, issuedAt, new Date(System.currentTimeMillis() + TOKEN_VALIDITY_IN_MILLISECONDS / 10), null, false);

        assertThat(filterWithToken(jwt).getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void aSessionIsNotExtendedWhenTheAccountNoLongerAllowsIt() throws Exception {
        // Covers deactivation, soft deletion and a credentials change since the session started - all of which cannot reach
        // an already-issued token and so are enforced at the rotation checkpoint.
        when(passkeyTokenRenewalService.mayExtendSessionForAccount(any(), any())).thenReturn(false);

        assertThat(filterWithToken(createRotationDueRememberMeToken()).getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void theCredentialIdIsCarriedInThePasskeyTokenSoItCanBeChecked() {
        String jwt = createRotationDuePasskeyToken("credential-1");

        assertThat(tokenProvider.getPasskeyCredentialId(jwt)).isEqualTo("credential-1");
    }

    @Test
    void aTokenWithoutACredentialIdIsPassedToTheValidatorAsNull() throws Exception {
        // Tokens issued before the claim existed carry no credential id; the validator decides what to do with them, and
        // this asserts the filter does not invent one.
        when(passkeyTokenRenewalService.mayExtendPasskeySession(null)).thenReturn(true);

        filterWithToken(createRotationDuePasskeyToken(null));

        verify(passkeyTokenRenewalService).mayExtendPasskeySession(null);
    }

    @Test
    void testJWTFilterWrongCookieName() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        String jwt = tokenProvider.createToken(authentication, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("wrong_jwt", jwt));
        request.setRequestURI("/api/core/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        jwtFilter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
