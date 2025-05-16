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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public class JWTFilterIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "jwtfilterintegrationtest";

    private static final long TOKEN_VALIDITY_IN_MILLISECONDS = 60000; // 60 seconds

    private static final long TOKEN_VALIDITY_REMEMBER_ME_IN_MILLISECONDS = 120000; // 120 seconds

    @Autowired
    private MockMvc mvc;

    // @Autowired
    // private RequestPostProcessor requestPostProcessor;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private JWTCookieService jwtCookieService;

    @Autowired
    private UserDetailsService userDetailsService;

    private JWTFilter jwtFilter;

    @BeforeEach
    void setup() {
        // ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", TOKEN_VALIDITY_IN_MILLISECONDS);
        // ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMillisecondsForRememberMe", TOKEN_VALIDITY_REMEMBER_ME_IN_MILLISECONDS);

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
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

    public ResultActions performMvcRequest(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mvc.perform(addRequestPostProcessorIfAvailable(requestBuilder));
    }

    private MockHttpServletRequestBuilder addRequestPostProcessorIfAvailable(MockHttpServletRequestBuilder request) {
        // if (requestPostProcessor != null) {
        // return request.with(requestPostProcessor);
        // }
        return request;
    }

    /**
     * We want to rotate a passkey-created token silently if it is used after 50% of its lifetime
     */
    @Test
    void testRotateTokenSilently_ShouldRotateToken() throws Exception {
        Authentication authentication = createWebAuthnAuthentication();

        Date issuedAt = new Date(System.currentTimeMillis() - 31000); // 31 seconds in the past
        Date expiration = new Date(issuedAt.getTime() + TOKEN_VALIDITY_IN_MILLISECONDS);

        String jwt = tokenProvider.createToken(authentication, issuedAt, expiration, null, true);
        assertThat(tokenProvider.getAuthenticatedWithPasskey(jwt)).isTrue();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, JWTFilter.JWT_COOKIE_NAME + "=" + jwt);

        MvcResult res = performMvcRequest(
                MockMvcRequestBuilders.get(new URI("/api/core/public/account")).params(params).headers(headers).cookie(new Cookie(JWTFilter.JWT_COOKIE_NAME, jwt)))
                .andExpect(status().is(HttpStatus.OK.value())).andReturn();

        MockHttpServletResponse response = res.getResponse();
        assertThat(response).isNotNull();

        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).isNotNull();
        ResponseCookie updatedCookie = CookieParser.parseSetCookieHeader(setCookieHeader);

        assertThat(setCookieHeader).contains(JWTFilter.JWT_COOKIE_NAME);
        assertThat(updatedCookie.getName()).isEqualTo(JWTFilter.JWT_COOKIE_NAME);
        assertThat(updatedCookie.getPath()).isEqualTo("/"); // TODO does that make sense?
        assertThat(updatedCookie.getMaxAge().getSeconds()).isEqualTo(2592000); // 30 days, TODO we would expect 60 seconds here instead
        assertThat(updatedCookie.isSecure()).isTrue();
        assertThat(updatedCookie.isHttpOnly()).isTrue();
        assertThat(updatedCookie.getSameSite()).isEqualTo("Lax");

        String updatedJwt = updatedCookie.getValue();
        assertThat(updatedJwt).isNotEmpty();
        assertThat(updatedJwt).isNotEqualTo(jwt);
        assertThat(tokenProvider.getAuthentication(updatedJwt).getPrincipal()).isEqualTo(tokenProvider.getAuthentication(jwt).getPrincipal());
        assertThat(tokenProvider.getAuthentication(updatedJwt).getAuthorities()).isEqualTo(authentication.getAuthorities());
        assertThat(tokenProvider.getAuthenticatedWithPasskey(updatedJwt)).isTrue();
        assertThat(tokenProvider.getIssuedAtDate(updatedJwt)).isCloseTo(issuedAt, 1000);
        assertThat(tokenProvider.getExpirationDate(updatedJwt)).isAfter(expiration);
        // assertThat(tokenProvider.getExpirationDate(updatedJwt)).isBefore(new Date(issuedAt.getTime() + TOKEN_VALIDITY_IN_MILLISECONDS * 2)); // TODO use the 30 days instead
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
