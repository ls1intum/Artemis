package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

public class AuthenticationIntegrationTestHelper {

    public static void authenticationCookieAssertions(Cookie cookie, boolean logoutCookie) {
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");

        if (logoutCookie) {
            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getValue()).isEmpty();
        }
        else {
            assertThat(cookie.getMaxAge()).isGreaterThan(0);
            assertThat(cookie.getValue()).isNotEmpty();
        }
    }
}
