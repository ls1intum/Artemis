package de.tum.in.www1.artemis.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.management.SecurityMetersService;
import de.tum.in.www1.artemis.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tech.jhipster.config.JHipsterProperties;

class TokenProviderTest {

    private static final long ONE_MINUTE = 60000;

    private Key key;

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
    void testReturnFalseWhenJWTisMissingClaims() {
        Authentication authentication = createAuthentication();
        Claims claimsForToken = Jwts.claims();
        claimsForToken.put(TokenProvider.ATTACHMENT_UNIT_ID_KEY, 2);
        String token = tokenProvider.createFileTokenWithCustomDuration(authentication, 30, claimsForToken);

        // attachment id and filename are required
        var requiredClaims = Map.of(TokenProvider.ATTACHMENT_UNIT_ID_KEY, 2, TokenProvider.FILENAME_KEY, "testfile");
        boolean isTokenValid = tokenProvider.validateTokenForAuthorityAndFile(token, requiredClaims);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTHasWrongValuesForClaims() {
        Authentication authentication = createAuthentication();
        Claims claimsForToken = Jwts.claims();
        claimsForToken.put(TokenProvider.ATTACHMENT_UNIT_ID_KEY, 2);
        String token = tokenProvider.createFileTokenWithCustomDuration(authentication, 30, claimsForToken);

        var requiredClaims = Map.of(TokenProvider.ATTACHMENT_UNIT_ID_KEY, 3);
        boolean isTokenValid = tokenProvider.validateTokenForAuthorityAndFile(token, requiredClaims);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnTrueWhenJWTIsValidAndHasCorrectClaims() {
        Authentication authentication = createAuthentication();
        Claims claimsForToken = Jwts.claims();
        claimsForToken.put(TokenProvider.ATTACHMENT_UNIT_ID_KEY, 2);
        claimsForToken.put(TokenProvider.FILENAME_KEY, "testfile");
        String token = tokenProvider.createFileTokenWithCustomDuration(authentication, 30, claimsForToken);

        var requiredClaims = Map.of(TokenProvider.ATTACHMENT_UNIT_ID_KEY, 2, TokenProvider.FILENAME_KEY, "testfile");
        boolean isTokenValid = tokenProvider.validateTokenForAuthorityAndFile(token, requiredClaims);

        assertThat(isTokenValid).isTrue();
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

    private Authentication createAuthentication() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
        return new UsernamePasswordAuthenticationToken("anonymous", "anonymous", authorities);
    }

    private String createUnsupportedToken() {
        return Jwts.builder().setPayload("payload").signWith(key, SignatureAlgorithm.HS512).compact();
    }

    private String createTokenWithDifferentSignature() {
        Key otherKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode("Xfd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"));

        return Jwts.builder().setSubject("anonymous").signWith(otherKey, SignatureAlgorithm.HS512).setExpiration(new Date(new Date().getTime() + ONE_MINUTE)).compact();
    }
}
