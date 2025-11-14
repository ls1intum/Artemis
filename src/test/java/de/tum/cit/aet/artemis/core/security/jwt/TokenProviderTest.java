package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.authentication.AuthenticationFactory;
import de.tum.cit.aet.artemis.core.management.SecurityMetersService;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tech.jhipster.config.JHipsterProperties;

class TokenProviderTest {

    private static final long ONE_MINUTE = 60000;

    private static final long TEN_MINUTES = 600000;

    private static final String USER_NAME = "anonymous";

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
        boolean isTokenValid = tokenProvider.validateTokenForAuthority(createTokenWithDifferentSignature(), null);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisMalformed() {
        Authentication authentication = AuthenticationFactory.createUsernamePasswordAuthentication(USER_NAME);
        String token = tokenProvider.createToken(authentication, false);
        String invalidToken = token.substring(1);
        boolean isTokenValid = tokenProvider.validateTokenForAuthority(invalidToken, null);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisExpired() {
        ReflectionTestUtils.setField(tokenProvider, "tokenValidityInMilliseconds", -ONE_MINUTE);

        Authentication authentication = AuthenticationFactory.createUsernamePasswordAuthentication(USER_NAME);
        String token = tokenProvider.createToken(authentication, false);

        boolean isTokenValid = tokenProvider.validateTokenForAuthority(token, null);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisUnsupported() {
        String unsupportedToken = createUnsupportedToken();

        boolean isTokenValid = tokenProvider.validateTokenForAuthority(unsupportedToken, null);

        assertThat(isTokenValid).isFalse();
    }

    @Test
    void testReturnFalseWhenJWTisInvalid() {
        boolean isTokenValid = tokenProvider.validateTokenForAuthority("", null);

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

    @Test
    void testGetIssuedAtDate() {
        Date issuedAt = new Date();
        String token = Jwts.builder().issuedAt(issuedAt).signWith(key, Jwts.SIG.HS512).compact();

        Date result = tokenProvider.getIssuedAtDate(token);

        assertThat(result).isNotNull().isCloseTo(issuedAt, 1000);
    }

    @Nested
    class GetToolsTests {

        @Test
        void shouldBeRetrievedSuccessfully() {
            ToolTokenType expectedTool = ToolTokenType.SCORPIO;
            String token = Jwts.builder().claim("tools", expectedTool.toString()).signWith(key, Jwts.SIG.HS512).compact();

            ToolTokenType actualTool = tokenProvider.getTools(token);

            assertThat(actualTool).isNotNull().isEqualTo(expectedTool);
        }

        @Test
        void shouldNotFailIfNull() {
            String token = Jwts.builder().claim("someDummyClaim", true).signWith(key, Jwts.SIG.HS512).compact();

            ToolTokenType actualTool = tokenProvider.getTools(token);

            assertThat(actualTool).isNull();
        }

    }

    @Nested
    class GetAuthenticationMethodTests {

        @Test
        void shouldBeRetrievedSuccessfully() {
            AuthenticationMethod expectedMethod = AuthenticationMethod.PASSKEY;
            String token = Jwts.builder().claim("auth-method", expectedMethod.toString()).signWith(key, Jwts.SIG.HS512).compact();

            AuthenticationMethod actualMethod = tokenProvider.getAuthenticationMethod(token);

            assertThat(actualMethod).isNotNull().isEqualTo(expectedMethod);
        }

        @Test
        void shouldNotFailIfNull() {
            AuthenticationMethod expectedMethod = null;
            String token = Jwts.builder().claim("auth-method", null).signWith(key, Jwts.SIG.HS512).compact();

            AuthenticationMethod actualMethod = tokenProvider.getAuthenticationMethod(token);

            assertThat(actualMethod).isNull();
        }

    }

    @Nested
    class IsPasskeySuperAdminApprovedTests {

        @Test
        void shouldReturnTrueWhenClaimIsTrue() {
            String token = Jwts.builder().claim(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, true).signWith(key, Jwts.SIG.HS512).compact();

            boolean isApproved = tokenProvider.isPasskeySuperAdminApproved(token);

            assertThat(isApproved).isTrue();
        }

        @Test
        void shouldReturnFalseWhenClaimIsFalse() {
            String token = Jwts.builder().claim(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, false).signWith(key, Jwts.SIG.HS512).compact();

            boolean isApproved = tokenProvider.isPasskeySuperAdminApproved(token);

            assertThat(isApproved).isFalse();
        }

        @Test
        void shouldReturnFalseWhenClaimIsNull() {
            String token = Jwts.builder().claim(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED, null).signWith(key, Jwts.SIG.HS512).compact();

            boolean isApproved = tokenProvider.isPasskeySuperAdminApproved(token);

            assertThat(isApproved).isFalse();
        }

        @Test
        void shouldReturnFalseWhenClaimIsMissing() {
            String token = Jwts.builder().claim("some-other-claim", true).signWith(key, Jwts.SIG.HS512).compact();

            boolean isApproved = tokenProvider.isPasskeySuperAdminApproved(token);

            assertThat(isApproved).isFalse();
        }

        @Test
        void shouldReturnFalseWhenTokenIsMalformed() {
            Authentication authentication = AuthenticationFactory.createUsernamePasswordAuthentication(USER_NAME);
            String token = tokenProvider.createToken(authentication, false);
            String malformedToken = token.substring(1);

            boolean isApproved = tokenProvider.isPasskeySuperAdminApproved(malformedToken);

            assertThat(isApproved).isFalse();
        }

    }

    @Nested
    class AuthenticationMethodTests {

        @Test
        void shouldSetPasswordMethod() {
            Authentication authentication = AuthenticationFactory.createUsernamePasswordAuthentication(USER_NAME);
            String token = tokenProvider.createToken(authentication, false);

            AuthenticationMethod authenticationMethod = tokenProvider.getAuthenticationMethod(token);

            assertThat(authenticationMethod).isEqualTo(AuthenticationMethod.PASSWORD);
        }

        @Test
        void shouldSetPasskeyMethod() {
            Authentication authentication = AuthenticationFactory.createWebAuthnAuthentication(USER_NAME);
            String token = tokenProvider.createToken(authentication, false);

            AuthenticationMethod authenticationMethod = tokenProvider.getAuthenticationMethod(token);

            assertThat(authenticationMethod).isEqualTo(AuthenticationMethod.PASSKEY);
        }

        @Test
        void shouldSetSaml2Method() {
            Authentication authentication = AuthenticationFactory.createSaml2Authentication(USER_NAME);
            String token = tokenProvider.createToken(authentication, false);

            AuthenticationMethod authenticationMethod = tokenProvider.getAuthenticationMethod(token);

            assertThat(authenticationMethod).isEqualTo(AuthenticationMethod.SAML2);
        }

    }

    private String createUnsupportedToken() {
        return Jwts.builder().content("payload").signWith(key, Jwts.SIG.HS512).compact();
    }

    private String createTokenWithDifferentSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode("Xfd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8"));

        return Jwts.builder().subject(USER_NAME).signWith(otherKey, Jwts.SIG.HS512).expiration(new Date(new Date().getTime() + ONE_MINUTE)).compact();
    }
}
