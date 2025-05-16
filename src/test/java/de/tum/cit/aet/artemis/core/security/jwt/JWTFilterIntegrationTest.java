package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;
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

public class JWTFilterIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "jwtfilterintegrationtest";

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds}")
    private long TOKEN_VALIDITY_IN_SECONDS;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me}")
    private long TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS;

    @Value("${artemis.user-management.passkey.token-validity-in-seconds-for-passkey}")
    private long TOKEN_VALIDITY_IN_SECONDS_FOR_PASSKEY;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenProvider tokenProvider;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    /**
     * TODO this should be a validation on server startup
     */
    @Test
    void verifyTokenValidityIsConfiguredProperly() {
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
     * We want to rotate a passkey-created token silently if it has used after 50% of its lifetime
     */
    @Test
    void testRotateTokenSilently_shouldRotateToken_ifMoreThanHalfOfLifetimeUsed() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        long moreThanHalfOfTokenValidityPassed = (long) (TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 0.6 * 1000);
        Date issuedAt = new Date(System.currentTimeMillis() - moreThanHalfOfTokenValidityPassed);
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, JWTFilter.JWT_COOKIE_NAME + "=" + jwt);

        MvcResult res = mvc
                .perform(MockMvcRequestBuilders.get(new URI("/api/core/public/account")).params(params).headers(headers).cookie(new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt)))
                .andExpect(status().is(HttpStatus.OK.value())).andReturn();

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        ResponseCookie updatedCookie = CookieParser.parseSetCookieHeader(setCookieHeader);

        assertThat(setCookieHeader).contains(JWTFilter.JWT_COOKIE_NAME);
        assertThat(updatedCookie.getName()).isEqualTo(JWTFilter.JWT_COOKIE_NAME);
        assertThat(updatedCookie.getPath()).isEqualTo("/"); // TODO does that make sense?
        assertThat(updatedCookie.getMaxAge().getSeconds()).isEqualTo(TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS);
        assertThat(updatedCookie.isSecure()).isTrue();
        assertThat(updatedCookie.isHttpOnly()).isTrue();
        assertThat(updatedCookie.getSameSite()).isEqualTo("Lax");

        String updatedJwt = updatedCookie.getValue();
        assertThat(updatedJwt).isNotEmpty();
        assertThat(updatedJwt).isNotEqualTo(jwt);
        assertThat(tokenProvider.getAuthentication(updatedJwt).getPrincipal()).isEqualTo(tokenProvider.getAuthentication(jwt).getPrincipal());
        assertThat(tokenProvider.getAuthentication(updatedJwt).getAuthorities()).isEqualTo(authentication.getAuthorities());
        assertThat(tokenProvider.getAuthenticatedWithPasskey(updatedJwt)).isTrue();
        assertThat(tokenProvider.getIssuedAtDate(updatedJwt)).isCloseTo(issuedAt, 1000); // should not have changed
        // IMPORTANT! The expiration date of the rotated token must be in the future, but not too far in the future
        assertThat(tokenProvider.getExpirationDate(updatedJwt)).isAfter(new Date(System.currentTimeMillis() + (long) (0.9 * TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000)));
        assertThat(tokenProvider.getExpirationDate(updatedJwt)).isBefore(new Date(System.currentTimeMillis() + TOKEN_VALIDITY_REMEMBER_ME_IN_SECONDS * 1000));
    }

    // TODO should not extend time beyond max passkey token lifetime

    // TODO should not rotate token if it comes from a bearer token (only from a cookie)

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

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, JWTFilter.JWT_COOKIE_NAME + "=" + jwt);

        MvcResult res = mvc
                .perform(MockMvcRequestBuilders.get(new URI("/api/core/public/account")).params(params).headers(headers).cookie(new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt)))
                .andExpect(status().is(HttpStatus.OK.value())).andReturn();

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();
        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNull();
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
