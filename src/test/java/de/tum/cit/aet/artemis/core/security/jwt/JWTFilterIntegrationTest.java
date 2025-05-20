package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import jakarta.servlet.http.Cookie;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * <p>
 * <strong>Integration test</strong> for the {@link JWTFilter} token rotation mechanism.
 * </p>
 *
 * <p>
 * <em>Note:</em> We assume that the following conditions hold:
 * </p>
 * <ul>
 * <li>{@link JWTFilterIntegrationTest#TOKEN_VALIDITY_IN_SECONDS} &lt; {@link JWTFilterIntegrationTest#TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS}</li>
 * <li>{@link JWTFilterIntegrationTest#TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS} &lt; {@link JWTFilterIntegrationTest#TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY}</li>
 * </ul>
 * <p>
 * If this does not hold, some calculations might break.
 * </p>
 */
public class JWTFilterIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "jwtfilterintegrationtest";

    /**
     * This can be any endpoint where the JWT filter is applied.
     */
    private static final String ENDPOINT_TO_TEST = "/api/core/public/account";

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds}") // 86400 when tests where written
    private long TOKEN_VALIDITY_IN_SECONDS;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me}") // 2592000 when tests where written
    private long TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS;

    @Value("${artemis.user-management.passkey.token-validity-in-seconds-for-passkey}") // 1555200 when tests where written
    private long TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenProvider tokenProvider;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    void testConfigurationContainsReasonableValues() {
        assertThat(TOKEN_VALIDITY_IN_SECONDS).isGreaterThan(60);
        assertThat(TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS).isGreaterThan(TOKEN_VALIDITY_IN_SECONDS);
        assertThat(TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY).isGreaterThan(0);
        assertThat(TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY).isGreaterThan(TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS);
    }

    // TODO make sure this method is shared instead
    private Authentication createWebAuthnAuthentication() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));

        String testUserName = TEST_PREFIX + "student1";

        PublicKeyCredentialUserEntity principal = mock(PublicKeyCredentialUserEntity.class);
        when(principal.getId()).thenReturn(new Bytes(testUserName.getBytes(StandardCharsets.UTF_8)));
        when(principal.getName()).thenReturn(testUserName);
        when(principal.getDisplayName()).thenReturn(testUserName);

        return new WebAuthnAuthentication(principal, authorities);
    }

    /**
     * <p>
     * We want to rotate a passkey-created token silently if it has used after 50% of its lifetime
     * </p>
     * Ensures that the token is rotated properly without changing authentication data or the issuedAt date
     */
    @Test
    void testRotateTokenSilently_shouldRotateToken_ifMoreThanHalfOfLifetimeUsed() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        long moreThanHalfOfTokenValidityPassed = (long) (TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 0.6 * 1000);
        Date issuedAt = new Date(System.currentTimeMillis() - moreThanHalfOfTokenValidityPassed);
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        MvcResult res = performRequestWithCookie(jwt);

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        ResponseCookie updatedCookie = CookieParser.parseSetCookieHeader(setCookieHeader);

        validateUpdatedCookie(updatedCookie, jwt, tokenProvider.getAuthentication(jwt), issuedAt, TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS);
        // values should not have changed except for the expiration date
        String updatedJwt = updatedCookie.getValue();
        // IMPORTANT! The expiration date of the rotated token must be in the future, but not too far in the future
        assertThat(tokenProvider.getExpirationDate(updatedJwt)).isAfter(new Date(System.currentTimeMillis() + (long) (0.9 * TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000)));
        assertThat(tokenProvider.getExpirationDate(updatedJwt)).isBefore(new Date(System.currentTimeMillis() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000));
    }

    /**
     * We shall never extend the token lifetime beyond the maximum passkey token lifetime
     */
    @Test
    void testRotateTokenSilently_shouldRotateToken_butConsiderMaxPasskeyTokenLifetime() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        long nowInMilliseconds = System.currentTimeMillis();
        Date issuedAt = new Date(nowInMilliseconds - (long) (TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY * 0.9 * 1000));
        Date expiration = new Date(nowInMilliseconds + (long) (TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 0.4 * 1000));

        long tokenLifetimeAlreadyUsedUpInMilliseconds = nowInMilliseconds - issuedAt.getTime();
        long expectedRemainingLifetimeInMilliseconds = TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY * 1000 - tokenLifetimeAlreadyUsedUpInMilliseconds;
        // if this is not the case, the test does not make sense - might happen if TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY or TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS is adjusted
        // -> adjust issuedAt and expiration accordingly in that case (no full-time for TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS left, last rotated token before passkey token lifetime
        // is reached)
        assertThat(expectedRemainingLifetimeInMilliseconds).isLessThan(TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY * 1000);
        assertThat(expectedRemainingLifetimeInMilliseconds).isLessThan(TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        MvcResult res = performRequestWithCookie(jwt);

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        ResponseCookie updatedCookie = CookieParser.parseSetCookieHeader(setCookieHeader);

        validateUpdatedCookie(updatedCookie, jwt, tokenProvider.getAuthentication(jwt), issuedAt, expectedRemainingLifetimeInMilliseconds / 1000);
        assertThat(updatedCookie.getMaxAge().getSeconds()).isLessThan(TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS);
        assertThat(updatedCookie.getMaxAge().getSeconds()).isLessThan(TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY);
        // allow a 15-second deviation -> can be increased in case the runners are even slower (magnitue of seconds does not matter here)
        // assertThat(updatedCookie.getMaxAge().getSeconds()).isCloseTo(, Offset.offset(15L));

        // values should not have changed except for the expiration date
        String updatedJwt = updatedCookie.getValue();
        // IMPORTANT! The expiration date of the rotated token must be in the future but must not exceed the maximum passkey token lifetime
        assertThat(tokenProvider.getExpirationDate(updatedJwt)).isAfter(new Date(System.currentTimeMillis() + (long) (expectedRemainingLifetimeInMilliseconds * 0.9)));
        assertThat(tokenProvider.getExpirationDate(updatedJwt)).isBefore(new Date(System.currentTimeMillis() + expectedRemainingLifetimeInMilliseconds));
    }

    @Test
    void testRotateTokenSilently_shouldNotRotateAnExpiredToken() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        long remainingValidityTimeOfTokenInMilliseconds = 1000;
        Date issuedAt = new Date(System.currentTimeMillis() - (TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY * 1000) + remainingValidityTimeOfTokenInMilliseconds);
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY * 1000 + remainingValidityTimeOfTokenInMilliseconds);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        Thread.sleep(remainingValidityTimeOfTokenInMilliseconds + 1000);

        MvcResult res = performRequestWithCookie(jwt);

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNull();
    }

    @Test
    void testRotateTokenSilently_shouldNotRotateToken_ifSuppliedByBearerToken() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        long moreThanHalfOfTokenValidityPassed = (long) (TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 0.6 * 1000);
        Date issuedAt = new Date(System.currentTimeMillis() - moreThanHalfOfTokenValidityPassed);
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);

        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(ENDPOINT_TO_TEST)).params(params).headers(headers)).andExpect(status().is(HttpStatus.OK.value()))
                .andReturn();

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNull();
    }

    /**
     * Ensure that the check cannot be bypassed by also passing the bearer token as Cookie; we do not want to rotate bearer tokens
     */
    @Test
    void testRotateTokenSilently_shouldNotRotateToken_ifSuppliedByBearerTokenAndCookie() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        long moreThanHalfOfTokenValidityPassed = (long) (TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 0.6 * 1000);
        Date issuedAt = new Date(System.currentTimeMillis() - moreThanHalfOfTokenValidityPassed);
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);

        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI(ENDPOINT_TO_TEST)).params(params).headers(headers).cookie(new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt)))
                .andExpect(status().is(HttpStatus.BAD_REQUEST.value())).andReturn();

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNull();
    }

    /**
     * We DO NOT want to rotate a passkey-created token silently if it has used LESS THAN 50% of its lifetime
     */
    @Test
    void testRotateTokenSilently_shouldNotRotateToken_ifLessThanHalfOfLifetimeUsed() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        long lessThanHalfOfTokenValidityPassed = (long) (TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 0.4 * 1000);
        Date issuedAt = new Date(System.currentTimeMillis() - lessThanHalfOfTokenValidityPassed);
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        MvcResult res = performRequestWithCookie(jwt);

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNull();
    }

    @Test
    void testRotateTokenSilently_shouldNotRotateToken_ifTokenWasNotCreatedFromPasskey() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(TEST_PREFIX + "student1", TEST_PREFIX + "student1",
                Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));

        long lessThanHalfOfTokenValidityPassed = (long) (TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 0.4 * 1000);
        Date issuedAt = new Date(System.currentTimeMillis() - lessThanHalfOfTokenValidityPassed);
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, null);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isFalse();

        MvcResult res = performRequestWithCookie(jwt);

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNull();
    }

    private Authentication createAuthentication(String username, String role) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        return new UsernamePasswordAuthenticationToken(username, username, authorities);
    }

    private Authentication createWebAuthnAuthentication(String username) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));

        PublicKeyCredentialUserEntity principal = mock(PublicKeyCredentialUserEntity.class);
        when(principal.getId()).thenReturn(new Bytes(username.getBytes(StandardCharsets.UTF_8)));
        when(principal.getName()).thenReturn(username);
        when(principal.getDisplayName()).thenReturn(username);

        return new WebAuthnAuthentication(principal, authorities);
    }

    private MvcResult performRequestWithCookie(String jwt) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(new URI(ENDPOINT_TO_TEST)).cookie(new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt))).andReturn();
    }

    private void validateUpdatedCookie(ResponseCookie updatedCookie, String originalJwt, Authentication authentication, Date issuedAt, long maxAgeInSeconds) {
        assertThat(updatedCookie.getName()).isEqualTo(JWTFilter.JWT_COOKIE_NAME);
        assertThat(updatedCookie.getPath()).isEqualTo("/");
        assertThat(updatedCookie.getMaxAge().getSeconds()).isCloseTo(maxAgeInSeconds, Offset.offset(15L)); // prevents that the test is flaky on slow runners
        assertThat(updatedCookie.isSecure()).isTrue();
        assertThat(updatedCookie.isHttpOnly()).isTrue();
        assertThat(updatedCookie.getSameSite()).isEqualTo("Lax");

        String updatedJwt = updatedCookie.getValue();
        assertThat(updatedJwt).isNotEmpty();
        assertThat(updatedJwt).isNotEqualTo(originalJwt);
        assertThat(tokenProvider.getAuthentication(updatedJwt).getPrincipal()).isEqualTo(authentication.getPrincipal());
        assertThat(tokenProvider.getAuthentication(updatedJwt).getAuthorities()).isEqualTo(authentication.getAuthorities());
        assertThat(tokenProvider.getAuthenticatedWithPasskey(updatedJwt)).isTrue();
        assertThat(tokenProvider.getIssuedAtDate(updatedJwt)).isCloseTo(issuedAt, 1000); // should not have changed, tolerance due to formatting
    }

    public static class CookieParser {

        public static ResponseCookie parseSetCookieHeader(String setCookieHeader) {
            String[] parts = setCookieHeader.split(";");
            String[] nameValue = parts[0].trim().split("=", 2);
            String name = nameValue[0];
            String value = nameValue.length > 1 ? nameValue[1] : "";

            ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value);

            for (int i = 1; i < parts.length; i++) {
                String[] attr = parts[i].trim().split("=", 2);
                String key = attr[0].trim().toLowerCase();
                String val = attr.length > 1 ? attr[1].trim() : "";

                switch (key) {
                    case "path":
                        builder.path(val);
                        break;
                    case "max-age":
                        builder.maxAge(Long.parseLong(val));
                        break;
                    case "expires":
                        // ResponseCookie does not directly support `Expires`, prefer Max-Age
                        break;
                    case "domain":
                        builder.domain(val);
                        break;
                    case "secure":
                        builder.secure(true);
                        break;
                    case "httponly":
                        builder.httpOnly(true);
                        break;
                    case "samesite":
                        builder.sameSite(val);
                        break;
                    default:
                        // Unknown attribute
                        break;
                }
            }

            return builder.build();
        }
    }
}
