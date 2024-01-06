package de.tum.in.www1.artemis.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.management.SecurityMetersService;
import de.tum.in.www1.artemis.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tech.jhipster.config.JHipsterProperties;

class TokenProviderSecurityMetersTest {

    private static final long ONE_MINUTE = 60000;

    private static final String INVALID_TOKENS_METER_EXPECTED_NAME = "security.authentication.invalid-tokens";

    private MeterRegistry meterRegistry;

    private TokenProvider tokenProvider;

    @BeforeEach
    void setup() {
        JHipsterProperties jHipsterProperties = new JHipsterProperties();
        String base64Secret = "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setBase64Secret(base64Secret);

        meterRegistry = new SimpleMeterRegistry();

        SecurityMetersService securityMetersService = new SecurityMetersService(meterRegistry);

        tokenProvider = new TokenProvider(jHipsterProperties, securityMetersService);
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));

        ReflectionTestUtils.setField(tokenProvider, "key", key);
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", ONE_MINUTE);
    }

    @Test
    void testValidTokenShouldNotCountAnything() {
        Collection<Counter> counters = meterRegistry.find(INVALID_TOKENS_METER_EXPECTED_NAME).counters();

        assertThat(aggregate(counters)).isZero();

        String validToken = createValidToken();

        tokenProvider.validateTokenForAuthority(validToken);

        assertThat(aggregate(counters)).isZero();
    }

    @Test
    void testTokenExpiredCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "expired").counter().count()).isZero();

        String expiredToken = createExpiredToken();

        tokenProvider.validateTokenForAuthority(expiredToken);

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "expired").counter().count()).isEqualTo(1);
    }

    @Test
    void testTokenUnsupportedCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "unsupported").counter().count()).isZero();

        String unsupportedToken = createUnsupportedToken();

        tokenProvider.validateTokenForAuthority(unsupportedToken);

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "unsupported").counter().count()).isEqualTo(1);
    }

    @Test
    void testTokenSignatureInvalidCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "invalid-signature").counter().count()).isZero();

        String tokenWithDifferentSignature = createTokenWithDifferentSignature();

        tokenProvider.validateTokenForAuthority(tokenWithDifferentSignature);

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "invalid-signature").counter().count()).isEqualTo(1);
    }

    @Test
    void testTokenMalformedCount() {
        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "malformed").counter().count()).isZero();

        String malformedToken = createMalformedToken();

        tokenProvider.validateTokenForAuthority(malformedToken);

        assertThat(meterRegistry.get(INVALID_TOKENS_METER_EXPECTED_NAME).tag("cause", "malformed").counter().count()).isEqualTo(1);
    }

    private String createValidToken() {
        Authentication authentication = createAuthentication();

        return tokenProvider.createToken(authentication, false);
    }

    private String createExpiredToken() {
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", -ONE_MINUTE);
        Authentication authentication = createAuthentication();
        return tokenProvider.createToken(authentication, false);
    }

    private Authentication createAuthentication() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
        return new UsernamePasswordAuthenticationToken("anonymous", "anonymous", authorities);
    }

    private String createUnsupportedToken() {
        SecretKey key = (SecretKey) ReflectionTestUtils.getField(tokenProvider, "key");
        return Jwts.builder().content("payload").signWith(key, Jwts.SIG.HS256).compact();
    }

    private String createMalformedToken() {
        String validToken = createValidToken();

        return "X" + validToken;
    }

    private String createTokenWithDifferentSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode("Xfd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"));

        return Jwts.builder().subject("anonymous").signWith(otherKey, Jwts.SIG.HS512).expiration(new Date(new Date().getTime() + ONE_MINUTE)).compact();
    }

    private double aggregate(Collection<Counter> counters) {
        return counters.stream().mapToDouble(Counter::count).sum();
    }
}
