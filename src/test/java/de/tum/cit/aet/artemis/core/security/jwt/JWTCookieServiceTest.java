package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;

class JWTCookieServiceTest {

    private final TokenProvider tokenProvider = mock(TokenProvider.class);

    @Test
    void shouldUseSecureCookiesOutsideDevelopmentByDefault() {
        var cookieService = cookieServiceWithProfiles("prod");

        assertThat(cookieService.buildLogoutCookie().isSecure()).isTrue();
    }

    @Test
    void shouldUseInsecureCookiesInDevelopmentByDefault() {
        var cookieService = cookieServiceWithProfiles("dev");

        assertThat(cookieService.buildLogoutCookie().isSecure()).isFalse();
    }

    @Test
    void shouldHonorExplicitCookieSecuritySetting() {
        var properties = new ArtemisProperties();
        properties.getSecurity().getAuthentication().getJwt().setCookieSecure(false);
        var cookieService = new JWTCookieService(tokenProvider, new MockEnvironment().withProperty("spring.profiles.active", "prod"), properties);

        assertThat(cookieService.buildLogoutCookie().isSecure()).isFalse();
    }

    private JWTCookieService cookieServiceWithProfiles(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new JWTCookieService(tokenProvider, environment, new ArtemisProperties());
    }
}
