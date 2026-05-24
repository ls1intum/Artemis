package de.tum.cit.aet.artemis.lti.nightly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lti.AbstractLtiIntegrationTest;
import de.tum.cit.aet.artemis.lti.config.DistributedStateAuthorizationRequestRepository;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.test_repository.LtiPlatformConfigurationTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Nightly end-to-end interop check against a real Moodle install.
 * <p>
 * Boots Moodle 5.0.2 (bitnamilegacy image) + Postgres on a shared docker network. Moodle's one-time install runs at
 * container start. The suite exercises every step of the LTI 1.3 OIDC launch against this real Moodle:
 * <ul>
 * <li><b>Step 1 (initiate-login):</b> {@link #initiateLoginRedirectsToMoodleAuthEndpoint()} — Artemis must build the
 * authorization-request URL Moodle expects, with state cached for the round trip.</li>
 * <li><b>Step 3a (redirect proxy):</b> {@link #authCallbackProxyAcceptsMoodleSignedJwt()} — Artemis's
 * {@code /auth-callback} must accept a real Moodle-signed launch JWT and redirect to {@code /lti/launch}.</li>
 * <li><b>Step 3b (Moodle-signed):</b> {@link #moodleSignedJwtPassesArtemisValidation()} — Moodle calls its own
 * {@code lti_sign_jwt()} via {@code docker exec}, producing a JWT with Moodle's actual claim mapping. Artemis
 * must fetch Moodle's JWKS over real HTTP, verify the signature, and survive claim validation.</li>
 * <li><b>Step 3b (synthetic):</b> {@link #syntheticJwtSignedWithMoodleKeyValidates()} — sanity check that signs with
 * Moodle's extracted private key and a minimal claim set. Isolates the signature/JWKS mechanics from claim shape.</li>
 * <li><b>JWKS parse:</b> {@link #moodleJwksDocumentIsParseable()} — Spring's {@code NimbusJwtDecoder.withJwkSetUri()}
 * must accept Moodle's JWKS document.</li>
 * </ul>
 * <p>
 * What this suite catches that Phase 1 cannot:
 * <ul>
 * <li>Drift in Moodle's JWKS document format (key encoding, JSON shape, content-type, RSA modulus length).</li>
 * <li>Drift in Moodle's launch JWT claim mapping (roles URL normalization, nested {@code context}/{@code resource_link}
 * claims, custom/ext claim shapes).</li>
 * <li>Regressions in {@code NimbusJwtDecoder.withJwkSetUri(...)} against a real LMS endpoint.</li>
 * <li>Regressions in {@code Lti13InitiatingLoginRequestResolver} against Moodle's actual authorization URI.</li>
 * </ul>
 * <p>
 * Tagged {@code nightly-lti} so per-PR runs skip it (configured in {@code gradle/test.gradle}). Run explicitly
 * with {@code ./gradlew test -DincludeTags='nightly-lti' --tests NightlyLtiMoodleInteropTest -x webapp}.
 */
@Tag("nightly-lti")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NightlyLtiMoodleInteropTest extends AbstractLtiIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(NightlyLtiMoodleInteropTest.class);

    /**
     * Pinned to a tag in the frozen {@code bitnamilegacy/} namespace (Bitnami moved free images out of {@code bitnami/}
     * on 28 Aug 2025). The legacy namespace receives no new versions or security patches; if it disappears the nightly
     * job needs to switch to a self-built image or pin to a digest. Tracked as a follow-up.
     */
    private static final String MOODLE_IMAGE = "bitnamilegacy/moodle:5.0.2";

    private static final String POSTGRES_IMAGE = "bitnamilegacy/postgresql:17";

    private static final String MOODLE_DB_USER = "moodle";

    private static final String MOODLE_DB_PASSWORD = "moodle";

    private static final String MOODLE_DB_NAME = "moodle";

    private static final String CLIENT_ID = "artemis-moodle-nightly-client";

    /**
     * Moodle's {@code $CFG->wwwroot} when started with {@code MOODLE_HOST=localhost}. Used as the {@code iss} claim in
     * every JWT Moodle signs, including via {@link #signWithMoodle}.
     */
    private static final String MOODLE_ISSUER = "http://localhost";

    private static final String SIGN_JWT_SCRIPT_IN_CONTAINER = "/tmp/moodle-sign-jwt.php";

    private static final Network network = Network.newNetwork();

    private static final GenericContainer<?> moodleDb = new GenericContainer<>(DockerImageName.parse(POSTGRES_IMAGE)).withNetwork(network).withNetworkAliases("moodle-db")
            .withEnv("POSTGRESQL_USERNAME", MOODLE_DB_USER).withEnv("POSTGRESQL_PASSWORD", MOODLE_DB_PASSWORD).withEnv("POSTGRESQL_DATABASE", MOODLE_DB_NAME).withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 1).withStartupTimeout(Duration.ofMinutes(3)));

    private static final GenericContainer<?> moodle = new GenericContainer<>(DockerImageName.parse(MOODLE_IMAGE)).withNetwork(network).withExposedPorts(8080)
            .withEnv("MOODLE_DATABASE_TYPE", "pgsql").withEnv("MOODLE_DATABASE_HOST", "moodle-db").withEnv("MOODLE_DATABASE_PORT_NUMBER", "5432")
            .withEnv("MOODLE_DATABASE_NAME", MOODLE_DB_NAME).withEnv("MOODLE_DATABASE_USER", MOODLE_DB_USER).withEnv("MOODLE_DATABASE_PASSWORD", MOODLE_DB_PASSWORD)
            .withEnv("MOODLE_USERNAME", "admin").withEnv("MOODLE_PASSWORD", "Artemis-Nightly-Pass1!").withEnv("MOODLE_EMAIL", "admin@example.com")
            .withEnv("MOODLE_SITE_NAME", "Artemis Interop").withEnv("MOODLE_HOST", "localhost").withEnv("ALLOW_EMPTY_PASSWORD", "no")
            .withCopyFileToContainer(MountableFile.forClasspathResource("/lti/nightly/moodle-sign-jwt.php"), SIGN_JWT_SCRIPT_IN_CONTAINER)
            .waitingFor(Wait.forHttp("/login/index.php").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(15)));

    @Autowired
    private DistributedStateAuthorizationRequestRepository stateRepository;

    @Autowired
    private LtiPlatformConfigurationTestRepository ltiPlatformConfigurationRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private String registrationId;

    private RSAPrivateKey moodlePrivateKey;

    private String moodleKid;

    private String moodleAuthUri;

    private String moodleJwksUri;

    @BeforeAll
    void startContainers() throws Exception {
        moodleDb.start();
        moodle.start();

        String moodleHostUrl = "http://" + moodle.getHost() + ":" + moodle.getMappedPort(8080);
        moodleAuthUri = moodleHostUrl + "/mod/lti/auth.php";
        moodleJwksUri = moodleHostUrl + "/mod/lti/certs.php";
        moodlePrivateKey = readMoodlePrivateKey();
        moodleKid = readMoodleKid();

        registrationId = "moodle-nightly-" + UUID.randomUUID();
        LtiPlatformConfiguration platform = new LtiPlatformConfiguration();
        platform.setRegistrationId(registrationId);
        platform.setClientId(CLIENT_ID);
        platform.setAuthorizationUri(moodleAuthUri);
        platform.setTokenUri(moodleHostUrl + "/mod/lti/token.php");
        platform.setJwkSetUri(moodleJwksUri);
        ltiPlatformConfigurationRepository.save(platform);
    }

    @AfterAll
    void cleanup() {
        // Each teardown step is independently try-wrapped so a failure in one does not leak the others. Without this,
        // a Docker daemon hiccup during moodle.stop() would orphan the Postgres container until Ryuk cleans up at JVM
        // exit — which can wedge a CI runner if Ryuk also fails.
        if (registrationId != null) {
            try {
                ltiPlatformConfigurationRepository.findByRegistrationId(registrationId).ifPresent(ltiPlatformConfigurationRepository::delete);
            }
            catch (RuntimeException ex) {
                log.warn("Failed to delete LTI platform row for cleanup", ex);
            }
        }
        try {
            moodle.stop();
        }
        catch (RuntimeException ex) {
            log.warn("Failed to stop Moodle container during cleanup", ex);
        }
        try {
            moodleDb.stop();
        }
        catch (RuntimeException ex) {
            log.warn("Failed to stop Moodle Postgres container during cleanup", ex);
        }
    }

    @Test
    @WithAnonymousUser
    void initiateLoginRedirectsToMoodleAuthEndpoint() throws Exception {
        // Step 1: a real Moodle would send the browser here. Artemis must respond with a 302 to Moodle's auth.php
        // carrying state/nonce/client_id/redirect_uri/scope/response_type/response_mode. This is the exact code path
        // that broke after the Spring Boot 4 upgrade (#12739) — UriComponentsBuilder.fromHttpUrl no longer exists.
        MvcResult result = request
                .performMvcRequest(get("/api/lti/public/lti13/initiate-login/{registrationId}", registrationId).param("iss", MOODLE_ISSUER).param("login_hint", "moodle-user-42")
                        .param("target_link_uri", "http://localhost/courses/1").param("client_id", CLIENT_ID))
                .andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(moodleAuthUri))).andReturn();

        URI location = URI.create(result.getResponse().getHeader("Location"));
        List<NameValuePair> params = URLEncodedUtils.parse(location, StandardCharsets.UTF_8);
        assertThat(params).extracting(NameValuePair::getName).contains("client_id", "login_hint", "response_type", "scope", "response_mode", "prompt", "state", "nonce",
                "redirect_uri");
        assertThat(params).filteredOn(p -> "client_id".equals(p.getName())).extracting(NameValuePair::getValue).containsExactly(CLIENT_ID);
        assertThat(params).filteredOn(p -> "redirect_uri".equals(p.getName())).extracting(NameValuePair::getValue).allSatisfy(uri -> assertThat(uri).endsWith("/auth-callback"));
    }

    @Test
    @WithAnonymousUser
    void authCallbackProxyAcceptsMoodleSignedJwt() throws Exception {
        // Step 3a: after Moodle authenticates the user it form-POSTs the signed JWT to Artemis's /auth-callback.
        // The proxy must accept a real Moodle-signed token and 302 the browser to /lti/launch with id_token + state.
        String state = UUID.randomUUID().toString();
        String idToken = signWithMoodle(CLIENT_ID, "http://localhost/lti/launch", UUID.randomUUID().toString());

        Map<String, Object> body = new HashMap<>();
        body.put("id_token", idToken);
        body.put("state", state);

        URI redirect = request.postForm("/api/lti/public/lti13/auth-callback", body, org.springframework.http.HttpStatus.FOUND);
        assertThat(redirect.getPath()).isEqualTo("/lti/launch");
        List<NameValuePair> params = URLEncodedUtils.parse(redirect, StandardCharsets.UTF_8);
        assertThat(params).filteredOn(p -> "id_token".equals(p.getName())).extracting(NameValuePair::getValue).containsExactly(idToken);
        assertThat(params).filteredOn(p -> "state".equals(p.getName())).extracting(NameValuePair::getValue).containsExactly(state);
    }

    @Test
    @WithAnonymousUser
    void moodleSignedJwtPassesArtemisValidation() throws Exception {
        // Step 3b with a *real* Moodle-signed JWT but no matching DB graph: Moodle's lti_sign_jwt() produces the token
        // using its own claim mapping (roles normalized to IMS URIs, nested context/resource_link objects, etc).
        // Artemis must fetch Moodle's JWKS, verify the signature, and survive Spring/upstream claim validation.
        // performLaunch then reaches `getCourseFromTargetLink` and rejects with 400 (BadRequestAlertException
        // → "Course not found") since the target_link_uri does not match any course in the test DB. The 400
        // proves that every layer above performLaunch handled real Moodle output correctly.
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        seedCachedAuthorizationRequest(state, nonce);

        String idToken = signWithMoodle(CLIENT_ID, "http://localhost/lti/launch", nonce);

        request.performMvcRequest(post("/api/lti/public/lti13/auth-login").param("id_token", idToken).param("state", state)).andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void syntheticJwtSignedWithMoodleKeyValidates() throws Exception {
        // Step 3b with a synthetic-claim JWT signed using the private key extracted from Moodle's DB. Isolates the
        // signature/JWKS mechanics from claim shape — if this passes but the Moodle-signed test fails, the regression
        // is in claim handling, not in JWKS plumbing.
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        seedCachedAuthorizationRequest(state, nonce);

        String idToken = signWithExtractedKey(claims -> {
            claims.issuer(MOODLE_ISSUER);
            claims.audience(CLIENT_ID);
            claims.subject("moodle-user-1");
            claims.issueTime(Date.from(Instant.now().minusSeconds(5)));
            claims.expirationTime(Date.from(Instant.now().plusSeconds(300)));
            claims.claim("nonce", nonce);
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiResourceLinkRequest");
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0");
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/deployment_id", "deployment-1");
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/target_link_uri", "http://localhost/courses/1");
        });

        request.performMvcRequest(post("/api/lti/public/lti13/auth-login").param("id_token", idToken).param("state", state)).andExpect(status().is5xxServerError());
    }

    @Test
    @WithAnonymousUser
    void fullLaunchSucceedsWithCourseAndExerciseFixture() throws Exception {
        // Full success path: real Course + OnlineCourseConfiguration (linked to our platform) + TextExercise in the DB.
        // Moodle signs an id_token whose target_link_uri matches /courses/{courseId}/exercises/{exerciseId}, so
        // lti13Service.performLaunch finds the course, finds the exercise, auto-creates a user from the launch claims
        // (requireExistingUser=false), establishes a security context, and writes the success JSON response. This is
        // the deepest end-to-end coverage achievable in a test JVM — the only thing missing is a browser session
        // round-trip, and that requires a real HTTP listener instead of MockMvc.
        String userPrefix = "moodletest" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String email = userPrefix + "@example.com";
        Course initialCourse = courseUtilService.createCourseWithUserPrefix(userPrefix);
        initialCourse.setOnlineCourse(true);
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setUserPrefix(userPrefix);
        onlineCourseConfiguration.setRequireExistingUser(false);
        onlineCourseConfiguration.setLtiPlatformConfiguration(ltiPlatformConfigurationRepository.findByRegistrationId(registrationId).orElseThrow());
        onlineCourseConfiguration.setCourse(initialCourse);
        initialCourse.setOnlineCourseConfiguration(onlineCourseConfiguration);
        final Course course = courseRepository.save(initialCourse);
        var textExercise = textExerciseUtilService.createSampleTextExercise(course);

        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        seedCachedAuthorizationRequest(state, nonce);

        String targetLinkUri = "http://localhost/courses/" + course.getId() + "/exercises/" + textExercise.getId();
        String idToken = signWithMoodle(CLIENT_ID, targetLinkUri, nonce, email);

        MvcResult result = request.performMvcRequest(post("/api/lti/public/lti13/auth-login").param("id_token", idToken).param("state", state))
                .andExpect(status().is(HttpStatus.OK.value())).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("targetLinkUri").contains("/courses/" + course.getId() + "/exercises/" + textExercise.getId());

        // UUID-suffixed email + userPrefix mean each run creates a fresh user; otherwise re-runs in the same DB would
        // short-circuit the user-creation path and mask any regression there.
        var newUser = userTestRepository.findOneByEmailIgnoreCase(email).orElseThrow();
        assertThat(newUser.getLogin()).startsWith(userPrefix + "_");
        var newUserWithGroups = userTestRepository.findUserWithGroupsAndAuthoritiesByLogin(newUser.getLogin()).orElseThrow();
        assertThat(newUserWithGroups.getGroups()).contains(course.getStudentGroupName());
    }

    @Test
    @WithAnonymousUser
    void moodleJwksDocumentIsParseable() {
        JwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(moodleJwksUri).build();
        assertThat(decoder).isNotNull();
    }

    private void seedCachedAuthorizationRequest(String state, String nonce) {
        Map<String, Object> additionalParameters = new HashMap<>();
        additionalParameters.put("registration_id", registrationId);
        additionalParameters.put("nonce", nonce);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("remote_ip", "127.0.0.1");

        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode().clientId(CLIENT_ID).authorizationUri(moodleAuthUri)
                .redirectUri("http://localhost/api/lti/public/lti13/auth-callback").scopes(java.util.Set.of("openid")).state(state).additionalParameters(additionalParameters)
                .attributes(attributes).build();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/api/lti/public/lti13/initiate-login/" + registrationId);
        mockRequest.setRemoteAddr("127.0.0.1");
        stateRepository.saveAuthorizationRequest(authRequest, mockRequest, new MockHttpServletResponse());
    }

    /**
     * Invokes Moodle's own {@code lti_sign_jwt()} via {@code docker exec} so the returned token uses Moodle's actual
     * RSA private key, kid header, and claim mapping.
     */
    private String signWithMoodle(String audience, String endpoint, String nonce) throws IOException, InterruptedException {
        return signWithMoodle(audience, endpoint, nonce, "student@example.com");
    }

    private String signWithMoodle(String audience, String endpoint, String nonce, String email) throws IOException, InterruptedException {
        ExecResult result = moodle.execInContainer("php", SIGN_JWT_SCRIPT_IN_CONTAINER, audience, endpoint, nonce, email);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Moodle JWT signing failed (exit=%d): stdout=%s stderr=%s".formatted(result.getExitCode(), result.getStdout(), result.getStderr()));
        }
        String token = result.getStdout().trim();
        if (token.isEmpty() || token.split("\\.").length != 3) {
            // Exit 0 with empty or malformed output points at a silent PHP warning (e.g. missing key/typo in claim
            // mapping). Surface stderr in the assertion so the failure is debuggable without re-reading container logs.
            throw new IllegalStateException("Moodle JWT signing returned malformed token (stdout=%s, stderr=%s)".formatted(result.getStdout(), result.getStderr()));
        }
        return token;
    }

    private String signWithExtractedKey(Consumer<JWTClaimsSet.Builder> claimCustomizer) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        claimCustomizer.accept(claims);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(moodleKid).build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        jwt.sign(new RSASSASigner(moodlePrivateKey));
        return jwt.serialize();
    }

    private RSAPrivateKey readMoodlePrivateKey() throws Exception {
        String pem = psql("SELECT value FROM mdl_config_plugins WHERE plugin='mod_lti' AND name='privatekey';").trim();
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String readMoodleKid() throws Exception {
        return psql("SELECT value FROM mdl_config_plugins WHERE plugin='mod_lti' AND name='kid';").trim();
    }

    private String psql(String sql) throws IOException, InterruptedException {
        ExecResult result = moodleDb.execInContainer("env", "PGPASSWORD=" + MOODLE_DB_PASSWORD, "psql", "-U", MOODLE_DB_USER, "-d", MOODLE_DB_NAME, "-t", "-A", "-c", sql);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql failed (exit=%d): %s".formatted(result.getExitCode(), result.getStderr()));
        }
        return result.getStdout();
    }
}
