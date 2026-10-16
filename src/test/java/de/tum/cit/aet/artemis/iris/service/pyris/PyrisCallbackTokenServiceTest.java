package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.management.SecurityMetersService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.FaqIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.GlobalSearchAnswerJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.LectureIngestionWebhookJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJobType;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for {@link PyrisCallbackTokenService}: the token Pyris authenticates its callbacks with.
 */
class PyrisCallbackTokenServiceTest {

    private static final String BASE64_SECRET = "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";

    private static final String RUN_ID = "4b6f0e0c-55f1-4a4e-9a0e-6f7c0f6d2b11";

    private TokenProvider tokenProvider;

    private PyrisCallbackTokenService service;

    private SecretKey callbackKey;

    private static TokenProvider tokenProvider(ArtemisProperties properties) {
        properties.getSecurity().getAuthentication().getJwt().setTokenValidityInSeconds(60);
        properties.getSecurity().getAuthentication().getJwt().setTokenValidityInSecondsForRememberMe(600);
        TokenProvider provider = new TokenProvider(properties, new SecurityMetersService(new SimpleMeterRegistry()));
        provider.init();
        return provider;
    }

    private static TokenProvider tokenProviderWithBase64Secret(String base64Secret) {
        ArtemisProperties properties = new ArtemisProperties();
        properties.getSecurity().getAuthentication().getJwt().setBase64Secret(base64Secret);
        return tokenProvider(properties);
    }

    @BeforeEach
    void setUp() {
        tokenProvider = tokenProviderWithBase64Secret(BASE64_SECRET);
        service = new PyrisCallbackTokenService(tokenProvider, 12, 10800);
        callbackKey = tokenProvider.deriveSigningKey(PyrisCallbackTokenService.KEY_PURPOSE);
    }

    private JwtBuilder validClaims() {
        Instant now = Instant.now();
        return Jwts.builder().issuer(PyrisCallbackTokenService.ISSUER).audience().add(PyrisCallbackTokenService.AUDIENCE).and().subject(RUN_ID)
                .claim(PyrisCallbackTokenService.JOB_TYPE_CLAIM, "chat").issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofMinutes(5))));
    }

    @Test
    void issuedTokenVerifiesForItsRunAndKind() {
        String token = service.issue(new ChatJob(RUN_ID, 1L, 2L, null, null, null, null));

        assertThat(token).doesNotContain(RUN_ID).as("the token is a signed JWT").matches("[\\w-]+\\.[\\w-]+\\.[\\w-]+");
        assertThat(service.verify(token)).contains(new PyrisCallbackTokenService.VerifiedCallback(RUN_ID, PyrisJobType.CHAT, null));
    }

    @Test
    void everyJobKindRoundTrips() {
        assertThat(service.verify(service.issue(new LectureIngestionWebhookJob(RUN_ID, 1L, 2L, 3L)))).map(PyrisCallbackTokenService.VerifiedCallback::type)
                .contains(PyrisJobType.LECTURE_INGESTION);
        assertThat(service.verify(service.issue(new FaqIngestionWebhookJob(RUN_ID, 1L, 2L)))).map(PyrisCallbackTokenService.VerifiedCallback::type)
                .contains(PyrisJobType.FAQ_INGESTION);
        assertThat(service.verify(service.issue(new GlobalSearchAnswerJob(RUN_ID, "student", "invocation-1"))))
                .contains(new PyrisCallbackTokenService.VerifiedCallback(RUN_ID, PyrisJobType.GLOBAL_SEARCH_ANSWER, "invocation-1"));
    }

    @Test
    void ingestionTokensOutliveTheAbsoluteIngestionTimeoutAndInteractiveTokensDoNot() {
        assertThat(service.validity(PyrisJobType.LECTURE_INGESTION)).isEqualTo(Duration.ofHours(13));
        assertThat(service.validity(PyrisJobType.FAQ_INGESTION)).isEqualTo(Duration.ofHours(13));
        assertThat(service.validity(PyrisJobType.CHAT)).isEqualTo(Duration.ofHours(6));
        assertThat(new PyrisCallbackTokenService(tokenProvider, 2, 5 * 3600).validity(PyrisJobType.LECTURE_INGESTION)).as("the longer of both limits counts")
                .isEqualTo(Duration.ofHours(6));
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        byte[] otherKey = new byte[64];
        otherKey[0] = 1;
        assertThat(service.verify(validClaims().signWith(Keys.hmacShaKeyFor(otherKey), Jwts.SIG.HS512).compact())).isEmpty();
    }

    @Test
    void acceptsAHandBuiltValidToken() {
        // The baseline for the rejection cases below: they differ from this token in one property each.
        assertThat(service.verify(validClaims().signWith(callbackKey, Jwts.SIG.HS512).compact())).isPresent();
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant past = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS);
        String token = validClaims().issuedAt(Date.from(past)).expiration(Date.from(past.plusSeconds(60))).signWith(callbackKey, Jwts.SIG.HS512).compact();
        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    void rejectsATokenAtItsExpiryBoundary() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String justExpired = validClaims().expiration(Date.from(now.minusSeconds(1))).signWith(callbackKey, Jwts.SIG.HS512).compact();
        String stillValid = validClaims().expiration(Date.from(now.plusSeconds(30))).signWith(callbackKey, Jwts.SIG.HS512).compact();
        assertThat(service.verify(justExpired)).as("no clock skew is allowed").isEmpty();
        assertThat(service.verify(stillValid)).isPresent();
    }

    @Test
    void rejectsAWrongOrMissingIssuer() {
        assertThat(service.verify(validClaims().issuer("pyris").signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
        assertThat(service.verify(validClaims().issuer(null).signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
    }

    @Test
    void rejectsAWrongOrMissingAudience() {
        assertThat(service.verify(validClaims().audience().single("artemis").signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
        Instant now = Instant.now();
        String withoutAudience = Jwts.builder().issuer(PyrisCallbackTokenService.ISSUER).subject(RUN_ID).claim(PyrisCallbackTokenService.JOB_TYPE_CLAIM, "chat")
                .expiration(Date.from(now.plusSeconds(300))).signWith(callbackKey, Jwts.SIG.HS512).compact();
        assertThat(service.verify(withoutAudience)).isEmpty();
    }

    @Test
    void rejectsMissingRunMissingKindUnknownKindOrMissingExpiry() {
        assertThat(service.verify(validClaims().subject(null).signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
        assertThat(service.verify(validClaims().subject(" ").signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
        assertThat(service.verify(validClaims().claim(PyrisCallbackTokenService.JOB_TYPE_CLAIM, null).signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
        assertThat(service.verify(validClaims().claim(PyrisCallbackTokenService.JOB_TYPE_CLAIM, "admin").signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
        assertThat(service.verify(validClaims().expiration(null).signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
    }

    @Test
    void rejectsAMissingIssueTimeOrAMalformedInvocation() {
        assertThat(service.verify(validClaims().issuedAt(null).signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
        assertThat(service.verify(validClaims().claim(PyrisCallbackTokenService.INVOCATION_CLAIM, 42).signWith(callbackKey, Jwts.SIG.HS512).compact())).isEmpty();
    }

    @Test
    void rejectsOtherAlgorithms() {
        assertThat(service.verify(validClaims().signWith(callbackKey, Jwts.SIG.HS256).compact())).as("HS256 with the right key").isEmpty();
        String unsecured = validClaims().compact();
        assertThat(unsecured).endsWith(".");
        assertThat(service.verify(unsecured)).as("alg none").isEmpty();
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = unsecured.split("\\.")[1];
        assertThat(service.verify(header + "." + payload + ".")).isEmpty();
    }

    @Test
    void rejectsValuesThatAreNoToken() {
        assertThat(service.verify(RUN_ID)).as("a plain run id").isEmpty();
        assertThat(service.verify("artemis_example_org-node1-1760000000000-AbCdE12345")).as("a value from before signed tokens").isEmpty();
        assertThat(service.verify("")).isEmpty();
    }

    @Test
    void sessionTokensAndCallbackTokensAreNeverAcceptedForEachOther() {
        var authentication = new UsernamePasswordAuthenticationToken("student", "", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        String sessionToken = tokenProvider.createToken(authentication, false);
        assertThat(service.verify(sessionToken)).as("a session token is no callback token").isEmpty();

        String callbackToken = service.issue(new ChatJob(RUN_ID, 1L, 2L, null, null, null, null));
        assertThat(tokenProvider.validateTokenForAuthority(callbackToken, null)).as("a callback token is no session token").isFalse();
    }

    @Test
    void theDerivedKeyIsNotTheSessionKeyAndIsTheSameOnEveryNode() {
        TokenProvider otherNode = tokenProviderWithBase64Secret(BASE64_SECRET);
        var otherNodeService = new PyrisCallbackTokenService(otherNode, 12, 10800);

        assertThat(callbackKey.getEncoded()).hasSize(64);
        String token = service.issue(new ChatJob(RUN_ID, 1L, 2L, null, null, null, null));
        assertThat(otherNodeService.verify(token)).as("another node with the same secret accepts the token").isPresent();

        var differentInstallation = new PyrisCallbackTokenService(tokenProviderWithBase64Secret(BASE64_SECRET.replace('f', 'e')), 12, 10800);
        assertThat(differentInstallation.verify(token)).as("an installation with another secret does not").isEmpty();
    }

    @Test
    void derivationWorksWithAPlainTextSecretToo() {
        ArtemisProperties properties = new ArtemisProperties();
        properties.getSecurity().getAuthentication().getJwt().setSecret("a-plain-text-secret-that-is-long-enough-for-hs512-signing-0123456789abcdef");
        var plainTextService = new PyrisCallbackTokenService(tokenProvider(properties), 12, 10800);

        String token = plainTextService.issue(new ChatJob(RUN_ID, 1L, 2L, null, null, null, null));
        assertThat(plainTextService.verify(token)).isPresent();
        assertThat(service.verify(token)).isEmpty();
    }
}
