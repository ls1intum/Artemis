package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.management.SecurityMetersService;
import de.tum.cit.aet.artemis.core.security.Role;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Covers the interaction between the configured absolute session ceiling and the lifetime an initial login cookie is
 * given. {@link JWTFilter} caps a <em>rotated</em> token, but refusing a rotation cannot shorten a token that is
 * already in the browser, so the ceiling has to apply at issuance too.
 * <p>
 * Also covers whether the cookie is marked secure, which follows the configured setting and otherwise the active
 * profile.
 */
class JWTCookieServiceTest {

    private static final long TOKEN_VALIDITY_IN_MILLISECONDS = 24 * 60 * 60 * 1000L; // one day

    private static final long REMEMBER_ME_VALIDITY_IN_MILLISECONDS = 7 * 24 * 60 * 60 * 1000L; // seven days

    /** High enough to leave the token validities above untouched, for the tests that are not about the ceiling. */
    private static final long UNRESTRICTIVE_CEILING_IN_SECONDS = 30 * 24 * 60 * 60L;

    private TokenProvider tokenProvider;

    @BeforeEach
    void setup() {
        ArtemisProperties jHipsterProperties = new ArtemisProperties();
        String base64Secret = "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setBase64Secret(base64Secret);

        tokenProvider = new TokenProvider(jHipsterProperties, new SecurityMetersService(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(tokenProvider, "key", Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret)));
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", TOKEN_VALIDITY_IN_MILLISECONDS);
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMillisecondsForRememberMe", REMEMBER_ME_VALIDITY_IN_MILLISECONDS);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("test-user", "test-password", List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()))));
    }

    private JWTCookieService cookieServiceWithCeiling(long maxSessionLifetimeInSeconds) {
        return new JWTCookieService(tokenProvider, new MockEnvironment(), new ArtemisProperties(), maxSessionLifetimeInSeconds);
    }

    /** The reported case: a one-day ceiling must not hand out a seven-day remember-me token. */
    @Test
    void clipsAnInitialRememberMeCookieToAShorterCeiling() {
        long ceilingInSeconds = 24 * 60 * 60L;
        JWTCookieService cookieService = cookieServiceWithCeiling(ceilingInSeconds);

        ResponseCookie cookie = cookieService.buildLoginCookie(true);

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(ceilingInSeconds));
    }

    /** The token itself has to be clipped too, not only the cookie the browser is asked to keep. */
    @Test
    void clipsTheTokenExpirationAndNotOnlyTheCookie() {
        long ceilingInSeconds = 24 * 60 * 60L;
        JWTCookieService cookieService = cookieServiceWithCeiling(ceilingInSeconds);
        long before = System.currentTimeMillis();

        ResponseCookie cookie = cookieService.buildLoginCookie(true);

        Date expiration = tokenProvider.getExpirationDate(cookie.getValue());
        assertThat(expiration).isBefore(new Date(before + REMEMBER_ME_VALIDITY_IN_MILLISECONDS));
        assertThat(expiration).isAfterOrEqualTo(new Date(before + ceilingInSeconds * 1000 - 5000));
    }

    /** A ceiling above the remember-me validity must leave the shorter configured validity untouched. */
    @Test
    void leavesRememberMeValidityAloneWhenTheCeilingIsLonger() {
        JWTCookieService cookieService = cookieServiceWithCeiling(UNRESTRICTIVE_CEILING_IN_SECONDS);

        ResponseCookie cookie = cookieService.buildLoginCookie(true);

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMillis(REMEMBER_ME_VALIDITY_IN_MILLISECONDS));
    }

    /** A session without remember-me is already shorter than a sane ceiling and must be unaffected. */
    @Test
    void leavesANonRememberMeSessionUnchanged() {
        JWTCookieService cookieService = cookieServiceWithCeiling(UNRESTRICTIVE_CEILING_IN_SECONDS);

        ResponseCookie cookie = cookieService.buildLoginCookie(false);

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMillis(TOKEN_VALIDITY_IN_MILLISECONDS));
    }

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
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        var cookieService = new JWTCookieService(tokenProvider, environment, properties, UNRESTRICTIVE_CEILING_IN_SECONDS);

        assertThat(cookieService.buildLogoutCookie().isSecure()).isFalse();
    }

    private JWTCookieService cookieServiceWithProfiles(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new JWTCookieService(tokenProvider, environment, new ArtemisProperties(), UNRESTRICTIVE_CEILING_IN_SECONDS);
    }
}
