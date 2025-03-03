package de.tum.cit.aet.artemis.core.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

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

    public static void toolTokenAssertions(TokenProvider tokenProvider, String token, long initialLifetime, ToolTokenType... tools) {
        assertThat(token).isNotNull();

        String[] toolClaims = tokenProvider.getClaim(token, "tools", String.class).split(",");
        assertThat(toolClaims).isNotEmpty();
        for (ToolTokenType tool : tools) {
            assertThat(toolClaims).contains(tool.toString());
        }

        var lifetime = tokenProvider.getExpirationDate(token).getTime() - System.currentTimeMillis();
        // assert that the token has a lifetime of less than a day
        assertThat(lifetime).isLessThan(24 * 60 * 60 * 1000);
        assertThat(lifetime).isLessThan(initialLifetime);
    }
}
