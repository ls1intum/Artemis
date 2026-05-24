# Nightly LTI Interop Coverage — Implementation Plan

**Status:** Draft proposal — not yet implemented.
**Author:** Stephan Krusche / Artemis maintainers, drafted with Claude.
**Context:** Follow-up to [#12769](https://github.com/ls1intum/Artemis/pull/12769) (Restore LTI Moodle integration broken by Spring Boot 4 upgrade) and [#12739](https://github.com/ls1intum/Artemis/issues/12739).

## Why

Issue #12739 surfaced because the LTI 1.3 Step 1 (third-party initiated login) path was not covered by any server integration test. The Spring Boot 4 upgrade (#12381) broke `UriComponentsBuilder.fromHttpUrl(String)` at runtime — a class of bug that startup-time tests, CodeQL, and Step 3 tests all miss. #12769 closed the Step 1 gap with a server integration test, but two adjacent gaps remain:

1. **Step 3b** (the OIDC auth-login filter, where the upstream library does JWT signature validation against the platform's JWKS) is still only covered by a single negative test (`oidcFlowFails_noRequestCached`). A future Spring Security upgrade that breaks `NimbusJwtDecoder.withJwkSetUri(...)` or `OidcLaunchFlowAuthenticationProvider` would not be caught.
2. **Real LMS interop** (Moodle, Canvas, edX) is never exercised in CI. Bugs that arise from the platform side — extra claims, URL escape quirks, new LTI Advantage extensions, deprecation of an LMS endpoint — go undetected until a user reports them.

This document scopes both gaps and proposes phased delivery.

## Scope

| Phase | Coverage | Run cadence | Effort |
|---|---|---|---|
| 0 | Tracking issue + this document | n/a | 15 min |
| 1 | Step 3b JWT validation (in-process JWKS) | every PR | 1 day |
| 2 | Real Moodle interop (Testcontainers) | nightly | 3 days |
| 3 | (optional) Canvas + edX matrix | nightly | 5 days |

Phases are independently mergeable. Each can ship as a separate PR.

---

## Phase 0 — Tracking issue

**Deliverable:** a single GitHub issue titled *"Nightly LTI interop coverage (post-#12769)"*. Labels: `core`, `lti`, `testing`. Owner: LTI Maintainers team. Body links to this document.

Becomes the parent for Phases 1–3 PRs.

---

## Phase 1 — Step 3b JWT validation, in-process JWKS

**Goal:** exercise the full Spring Security filter chain through the upstream `NimbusJwtDecoder` JWT validation branch, with no mocks of Spring or the upstream LTI library. Catches Spring 6→7-style API removals on the deepest Artemis LTI code path.

**Approach:** runs on **every PR** in the `server-tests` job (no infrastructure cost).

**Mocked**: nothing in the Spring or upstream LTI code path. The only fixture is an in-process HTTP server impersonating an LMS JWKS endpoint and a real RSA signer.

**File:** `src/test/java/de/tum/cit/aet/artemis/lti/Lti13Step3JwtValidationIntegrationTest.java`

### Exact implementation

```java
package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.test.context.support.WithAnonymousUser;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.lti.config.DistributedStateAuthorizationRequestRepository;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;

/**
 * Step 3 of the LTI 1.3 OIDC launch — id_token signature + claim validation against the platform's JWKS.
 * <p>
 * Exercises the entire upstream Spring Security filter chain end-to-end with NO mocks:
 * <ul>
 *   <li>A real local HTTP server (Java {@link HttpServer}) hosts a JWKS document containing a freshly-generated
 *       RSA public key.</li>
 *   <li>The test signs an id_token with the matching private key — same way a real LMS would.</li>
 *   <li>The cached {@code OAuth2AuthorizationRequest} is seeded into the production
 *       {@link DistributedStateAuthorizationRequestRepository} bean — the same store Step 1 writes to in
 *       production.</li>
 *   <li>The test POSTs the id_token + state to {@code /api/lti/public/lti13/auth-login}. Artemis routes it
 *       through {@code Lti13LaunchFilter} → upstream {@code OAuth2LoginAuthenticationFilter} →
 *       {@code OidcLaunchFlowAuthenticationProvider} → real {@code NimbusJwtDecoder.withJwkSetUri(...)} which
 *       fetches our JWKS over the network and validates the signature.</li>
 * </ul>
 */
class Lti13Step3JwtValidationIntegrationTest extends AbstractLtiIntegrationTest {

    private static final String AUTH_URI = "https://platform.example.com/mod/lti/auth.php";
    private static final String TOKEN_URI = "https://platform.example.com/mod/lti/token.php";
    private static final String CLIENT_ID = "artemis-jwt-test-client";
    private static final String ISSUER = "https://platform.example.com";

    private static HttpServer jwksServer;
    private static String jwksUri;
    private static RSAPrivateKey privateKey;
    private static String keyId;

    @Autowired
    private DistributedStateAuthorizationRequestRepository stateRepository;

    @BeforeAll
    static void startJwksServer() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        keyId = UUID.randomUUID().toString();
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        String jwks = new JWKSet(jwk).toPublicJWKSet().toString();

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks.json", exchange -> {
            byte[] body = jwks.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        jwksServer.start();
        jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks.json";
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    @WithAnonymousUser
    void step3ValidatesSignedIdTokenAgainstJwks() throws Exception {
        String registrationId = "test-platform-" + UUID.randomUUID();
        savePlatform(registrationId);

        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        seedCachedAuthorizationRequest(registrationId, state, nonce);

        String idToken = signIdToken(claims -> {
            claims.issuer(ISSUER);
            claims.audience(CLIENT_ID);
            claims.subject("user-42");
            claims.issueTime(Date.from(Instant.now().minusSeconds(5)));
            claims.expirationTime(Date.from(Instant.now().plusSeconds(300)));
            claims.claim("nonce", nonce);
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiResourceLinkRequest");
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0");
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/deployment_id", "deployment-1");
            claims.claim("https://purl.imsglobal.org/spec/lti/claim/target_link_uri", "http://localhost/courses/1");
        });

        request.performMvcRequest(post("/api/lti/public/lti13/auth-login")
                        .param("id_token", idToken)
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/lti/launch")));
    }

    @Test
    @WithAnonymousUser
    void step3RejectsTokenSignedWithWrongKey() throws Exception {
        String registrationId = "test-platform-" + UUID.randomUUID();
        savePlatform(registrationId);
        String state = UUID.randomUUID().toString();
        seedCachedAuthorizationRequest(registrationId, state, "nonce");

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPrivateKey rogueKey = (RSAPrivateKey) gen.generateKeyPair().getPrivate();

        String idToken = signIdTokenWithKey(rogueKey, claims -> {
            claims.issuer(ISSUER);
            claims.audience(CLIENT_ID);
            claims.expirationTime(Date.from(Instant.now().plusSeconds(60)));
            claims.issueTime(Date.from(Instant.now().minusSeconds(5)));
            claims.claim("nonce", "nonce");
        });

        request.performMvcRequest(post("/api/lti/public/lti13/auth-login")
                        .param("id_token", idToken)
                        .param("state", state))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @WithAnonymousUser
    void step3RejectsExpiredToken() throws Exception {
        String registrationId = "test-platform-" + UUID.randomUUID();
        savePlatform(registrationId);
        String state = UUID.randomUUID().toString();
        seedCachedAuthorizationRequest(registrationId, state, "nonce");

        String idToken = signIdToken(claims -> {
            claims.issuer(ISSUER);
            claims.audience(CLIENT_ID);
            claims.issueTime(Date.from(Instant.now().minusSeconds(3600)));
            claims.expirationTime(Date.from(Instant.now().minusSeconds(60)));
            claims.claim("nonce", "nonce");
        });

        request.performMvcRequest(post("/api/lti/public/lti13/auth-login")
                        .param("id_token", idToken)
                        .param("state", state))
                .andExpect(status().is5xxServerError());
    }

    private void savePlatform(String registrationId) {
        LtiPlatformConfiguration platform = new LtiPlatformConfiguration();
        platform.setRegistrationId(registrationId);
        platform.setClientId(CLIENT_ID);
        platform.setAuthorizationUri(AUTH_URI);
        platform.setTokenUri(TOKEN_URI);
        platform.setJwkSetUri(jwksUri);
        ltiPlatformConfigurationRepository.save(platform);
    }

    private void seedCachedAuthorizationRequest(String registrationId, String state, String nonce) {
        Map<String, Object> additionalParameters = new HashMap<>();
        additionalParameters.put("registration_id", registrationId);
        additionalParameters.put("nonce", nonce);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("remote_ip", "127.0.0.1");

        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId(CLIENT_ID)
                .authorizationUri(AUTH_URI)
                .redirectUri("http://localhost/api/lti/public/lti13/auth-callback")
                .scopes(java.util.Set.of("openid"))
                .state(state)
                .additionalParameters(additionalParameters)
                .attributes(attributes)
                .build();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST",
                "/api/lti/public/lti13/initiate-login/" + registrationId);
        mockRequest.setRemoteAddr("127.0.0.1");
        stateRepository.saveAuthorizationRequest(authRequest, mockRequest, new MockHttpServletResponse());
    }

    private String signIdToken(java.util.function.Consumer<JWTClaimsSet.Builder> claimCustomizer) throws Exception {
        return signIdTokenWithKey(privateKey, claimCustomizer);
    }

    private String signIdTokenWithKey(RSAPrivateKey signingKey,
                                       java.util.function.Consumer<JWTClaimsSet.Builder> claimCustomizer) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        claimCustomizer.accept(claims);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
```

### Why this exact shape

1. **JWKS over real HTTP (no library mock).** `NimbusJwtDecoder.withJwkSetUri(jwksUri)` issues an actual outbound HTTP `GET` to fetch the JWKS, parses it with Nimbus, then verifies the JWT signature. The JDK `com.sun.net.httpserver.HttpServer` on `127.0.0.1` causes the decoder to hit a real network socket and parse a real JWKS — same code path as production against Moodle's `/mod/lti/certs.php`.
2. **No new dependencies.** `com.sun.net.httpserver.HttpServer` is part of the JDK (`jdk.httpserver` module), and `com.nimbusds:nimbus-jose-jwt:10.9` is already on Artemis's classpath via `Lti13TokenRetriever`.
3. **State seeding uses the production write path.** `stateRepository.saveAuthorizationRequest(...)` is the exact method `OAuth2AuthorizationRequestRedirectFilter.sendRedirectForAuthorization` calls during Step 1. Breaks if anyone changes the state-repo write contract.
4. **Three failure modes covered.** Happy path proves the JWT validation branch runs; wrong-key proves signature verification actually runs; expired-token proves the `OidcTokenValidator` is wired in.
5. **Per-PR runnable.** ~1 s total (RSA keygen is the slowest step at ~300 ms). Lives in `server-tests`, not in the nightly job.

### Caveat

`step3RejectsTokenSignedWithWrongKey` and `step3RejectsExpiredToken` assert `is5xxServerError()` because `Lti13LaunchFilter.doFilterInternal` currently catches `OAuth2AuthenticationException` and maps it to `SC_INTERNAL_SERVER_ERROR`. A follow-up cleanup could rewrite that mapping to `SC_UNAUTHORIZED`; the test assertion would tighten with it. For now it pins down the current Artemis behaviour explicitly.

---

## Phase 2 — Real Moodle interop, nightly Testcontainers

**Goal:** catch real Moodle interop drift (claim shape changes, URL escape quirks, new spec requirements) — bugs Phase 1 cannot find because Phase 1 owns the "platform" side.

### Files

```
.github/workflows/nightly-lti-interop.yml                                # cron job
src/test/java/de/tum/cit/aet/artemis/lti/nightly/                        # tagged tests
  NightlyLtiMoodleInteropTest.java
  fixtures/MoodleSetup.java                                              # bootstrap helper
```

### Workflow

`.github/workflows/nightly-lti-interop.yml`:

```yaml
name: Nightly LTI Interop

on:
  schedule:
    - cron: '0 3 * * *'        # 03:00 UTC daily
  workflow_dispatch:           # manual ad-hoc runs

concurrency:
  group: nightly-lti-interop
  cancel-in-progress: false

permissions:
  contents: read

jobs:
  moodle-interop:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '25'
          cache: gradle
      - name: Run nightly LTI Moodle interop suite
        run: ./gradlew test -DincludeTags='nightly-lti' -x webapp
      - name: Upload JUnit reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: nightly-lti-reports
          path: build/reports/tests/test
      - name: Notify on failure
        if: failure()
        uses: rtCamp/action-slack-notify@v2
        env:
          SLACK_WEBHOOK: ${{ secrets.LTI_NIGHTLY_SLACK_WEBHOOK }}
          SLACK_TITLE: "Nightly LTI interop FAILED on develop"
          SLACK_COLOR: danger
```

### Test class

`NightlyLtiMoodleInteropTest` — JUnit `@Tag("nightly-lti")` so per-PR runs skip it:

- **Container**: `bitnami/moodle:4.3` (Moodle LTS, ~1.8 GB compressed). Boot ~150 s due to one-time install.
- **Postgres for Moodle**: a separate `postgres:17-alpine` container (Moodle's bitnami image supports Postgres via env vars).
- **`MoodleSetup.java`** bootstrap helper:
  - Waits for Moodle health endpoint (`/login/index.php` 200).
  - Calls Moodle's external functions API (`/webservice/rest/server.php?wstoken=...&wsfunction=core_course_create_courses`) to create a course.
  - Calls `mod_lti_create_tool_type` to register Artemis as an LTI 1.3 tool: `client_id`, `target_link_uri`, JWKS URL, `login_initiation_url`.
  - Returns the Moodle tool registration ID and course ID.
- **Artemis side**: a transient `LtiPlatformConfiguration` row with Moodle's `authorization_uri`, `token_uri`, `jwk_set_uri` is inserted in `@BeforeAll`.
- **Test action**: hit Moodle's launch URL (which generates a signed JWT and form-posts to Artemis's `/auth-callback`).
- **Assertions**: full round-trip — Moodle 302s to Artemis's `/initiate-login`, Artemis redirects browser back to Moodle's auth endpoint, Moodle's auth endpoint form-posts to Artemis's `/auth-callback`, Artemis ends at `/lti/launch?...` with a populated `LtiOauth2User` session.
- **Teardown**: `ltiPlatformConfigurationRepository.deleteByRegistrationId(...)` for *only* the row inserted here; Moodle container stops via Testcontainers' `@Container`.

### Estimated runtime

- Cold: ~6 min (Moodle install + boot) + ~2 min per launch test.
- Workflow timeout: 30 min — comfortable buffer.

### Effort estimate

- **Day 1**: Moodle Testcontainer + bootstrap script (most of the friction is around Moodle's REST API tokens and admin scope).
- **Day 2**: end-to-end launch test, assertions, debugging timing issues.
- **Day 3**: workflow file, Slack secret setup, README documentation in `documentation/docs/developer/guidelines/lti.md`.

### Operational requirements

1. **Slack webhook**: register `LTI_NIGHTLY_SLACK_WEBHOOK` as a repository secret. Without alerting, a nightly job that quietly red-screens is worse than no job at all.
2. **Owner**: assign the LTI Maintainers team as the default escalation for failures (CODEOWNERS line for the workflow file).
3. **Flakiness budget**: a first-class flake-detection step — if the job fails, retry once before posting to Slack. Three consecutive failures auto-open an issue (via `actions/github-script@v7`).
4. **Disk**: `ubuntu-latest` runners have 14 GB; Moodle + Postgres + Artemis WAR + Docker cache stays under 8 GB.
5. **Cost**: Artemis is a public repo — `ubuntu-latest` minutes are free. 30 min/day × 365 ≈ 180 hr/yr, well within unlimited tier.
6. **Maintenance**: Moodle releases happen roughly twice a year. Pin to a specific minor (e.g. `4.3.x`) to absorb security patches without API drift. Rebump on majors as a small follow-up PR.

---

## Phase 3 — Multi-LMS expansion (optional)

If Phase 2 proves valuable, add edX (`edxops/devstack`) and Canvas (`instructure/canvas-lms`) containers on the same nightly. Each LMS adds 5–10 min; the workflow stays under 30 min total via `strategy: matrix:` for parallel execution.

---

## Not in scope

- **Per-PR Moodle boot**: too slow, no incremental value over Phase 1.
- **Playwright UI-driven interop**: brittle, slow, and the UI surface area is wide. Phase 2's API-driven approach is strictly better for regression detection.
- **External LMS hosted by IMS Global** (https://lti-ri.imsglobal.org/): requires Artemis to be internet-reachable from IMS, which CI runners aren't. Better suited for periodic staging smoke tests.

---

## Exit criteria

- **Phase 1**: `Lti13Step3JwtValidationIntegrationTest` runs green on every PR. Step 3 happy path now has coverage — closes the gap explicitly called out in `Lti13LaunchIntegrationTest`'s class-level Javadoc.
- **Phase 2**: nightly job runs 7 consecutive nights without flake on `develop`. Slack alerting verified by intentionally breaking a test.
- Tracking issue closed.

---

## References

- Triggering bug: [#12739 — LTI/Moodle Integration broken](https://github.com/ls1intum/Artemis/issues/12739)
- Bugfix PR: [#12769 — Restore LTI Moodle integration broken by Spring Boot 4 upgrade](https://github.com/ls1intum/Artemis/pull/12769)
- Spring Boot 4 upgrade: [#12381](https://github.com/ls1intum/Artemis/pull/12381)
- Upstream library: [oxctl/spring-security-lti13](https://github.com/oxctl/spring-security-lti13) (0.3.4)
- Upstream Spring 7 PR: [oxctl/spring-security-lti13#60](https://github.com/oxctl/spring-security-lti13/pull/60)
- LTI 1.3 spec: https://www.imsglobal.org/spec/lti/v1p3/
- OIDC Third-Party-Initiated Login: https://www.imsglobal.org/spec/security/v1p0/#step-1-third-party-initiated-login
