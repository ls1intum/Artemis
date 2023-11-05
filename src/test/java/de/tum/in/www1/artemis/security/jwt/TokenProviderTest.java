package de.tum.in.www1.artemis.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tech.jhipster.config.JHipsterProperties;

class TokenProviderTest {

    private static final long ONE_MINUTE = 60000;

    private static final long TEN_MINUTES = 600000;

    private SecretKey key;

    private TokenProvider tokenProvider;

    @BeforeEach
    void setup() {
        JHipsterProperties jHipsterProperties = new JHipsterProperties();
        String base64Secret = "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setBase64Secret(base64Secret);

        SecurityMetersService securityMetersService = new SecurityMetersService(new SimpleMeterRegistry());

        tokenProvider = new TokenProvider(jHipsterProperties, securityMetersService);
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));

        ReflectionTestUtils.setField(tokenProvider, "key", key);
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", ONE_MINUTE);
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMillisecondsForRememberMe", TEN_MINUTES);

    }

    @Test
    void testReturnFalseWhenJWThasInvalidSignature() {
        boolean isTokenValid = tokenProvider.validateTokenForAuthority(createTokenWithDifferentSignature());

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisMalformed() {
        Authentication authentication = createAuthentication();
        String token = tokenProvider.createToken(authentication, false);
        String invalidToken = token.substring(1);
        boolean isTokenValid = tokenProvider.validateTokenForAuthority(invalidToken);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisExpired() {
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", -ONE_MINUTE);

        Authentication authentication = createAuthentication();
        String token = tokenProvider.createToken(authentication, false);

        boolean isTokenValid = tokenProvider.validateTokenForAuthority(token);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisUnsupported() {
        String unsupportedToken = createUnsupportedToken();

        boolean isTokenValid = tokenProvider.validateTokenForAuthority(unsupportedToken);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisInvalid() {
        boolean isTokenValid = tokenProvider.validateTokenForAuthority("");

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testKeyIsSetFromSecretWhenSecretIsNotEmpty() {
        final String secret = "NwskoUmKHZtzGRKJKVjsJF7BtQMMxNWi";
        JHipsterProperties jHipsterProperties = new JHipsterProperties();
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setSecret(secret);

        SecurityMetersService securityMetersService = new SecurityMetersService(new SimpleMeterRegistry());

        TokenProvider tokenProvider = new TokenProvider(jHipsterProperties, securityMetersService);
        tokenProvider.init();

        Key key = (Key) ReflectionTestUtils.getField(tokenProvider, "key");
        assertThat(key).isNotNull().isEqualTo(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testKeyIsSetFromBase64SecretWhenSecretIsEmpty() {
        final String base64Secret = "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";
        JHipsterProperties jHipsterProperties = new JHipsterProperties();
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setBase64Secret(base64Secret);

        SecurityMetersService securityMetersService = new SecurityMetersService(new SimpleMeterRegistry());

        TokenProvider tokenProvider = new TokenProvider(jHipsterProperties, securityMetersService);
        tokenProvider.init();

        Key key = (Key) ReflectionTestUtils.getField(tokenProvider, "key");
        assertThat(key).isNotNull().isEqualTo(Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret)));
    }

    @Test
    void testGetTokenValidityRememberMe() {
        long validity = tokenProvider.getTokenValidity(true);

        assertThat(validity).isEqualTo(TEN_MINUTES);
    }

    @Test
    void testGetTokenValidityNotRememberMe() {
        long validity = tokenProvider.getTokenValidity(false);

        assertThat(validity).isEqualTo(ONE_MINUTE);
    }

    private Authentication createAuthentication() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
        return new UsernamePasswordAuthenticationToken("anonymous", "anonymous", authorities);
    }

    private String createUnsupportedToken() {
        return Jwts.builder().content("payload").signWith(key, Jwts.SIG.HS512).compact();
    }

    private String createTokenWithDifferentSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode("Xfd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"));

        return Jwts.builder().subject("anonymous").signWith(otherKey, Jwts.SIG.HS512).expiration(new Date(new Date().getTime() + ONE_MINUTE)).compact();
    }
}
