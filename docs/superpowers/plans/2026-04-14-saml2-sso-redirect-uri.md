# SAML2 SSO Redirect URI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable external clients (VS Code extension, mobile apps) to receive a JWT after SAML2 SSO login via a configurable `redirect_uri` parameter.

**Architecture:** A custom `AuthenticationSuccessHandler` (extending `SimpleUrlAuthenticationSuccessHandler`) checks for a nonce in RelayState. If present, it looks up the validated `redirect_uri` from a Hazelcast distributed map, mints a JWT via `TokenProvider`, and redirects. If absent, it calls `super.onAuthenticationSuccess()` preserving the existing web flow. A custom `Saml2AuthenticationRequestResolver` validates and stores the `redirect_uri` before the IdP redirect, placing only a UUID nonce in RelayState.

**Tech Stack:** Spring Boot 3.5, Spring Security SAML2, Hazelcast, Java 25

**Spec:** `docs/superpowers/specs/2026-04-14-saml2-sso-redirect-uri-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `src/main/java/de/tum/cit/aet/artemis/core/config/SAML2Properties.java` | Modify | Add `allowedRedirectSchemes` and `externalTokenRememberMe` fields |
| `src/main/java/de/tum/cit/aet/artemis/core/repository/saml2/HazelcastSaml2RedirectUriRepository.java` | Create | Hazelcast-backed nonce → redirect_uri store |
| `src/main/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2RedirectUriValidator.java` | Create | Validate redirect_uri (scheme, length, syntax, fragments) |
| `src/main/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2ExternalClientAuthenticationSuccessHandler.java` | Create | Success handler: external redirect or fallback to `/` |
| `src/main/java/de/tum/cit/aet/artemis/core/config/SAML2Configuration.java` | Modify | Wire success handler and auth request resolver |
| `src/main/resources/config/application-saml2.yml` | Modify | Add new config properties with defaults |
| `src/test/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2RedirectUriValidatorTest.java` | Create | Unit tests for URI validation |
| `src/test/java/de/tum/cit/aet/artemis/core/repository/saml2/HazelcastSaml2RedirectUriRepositoryTest.java` | Create | Unit tests for Hazelcast nonce store |
| `src/test/java/de/tum/cit/aet/artemis/core/authentication/Saml2ExternalRedirectIntegrationTest.java` | Create | Integration tests for the full external redirect flow |

---

### Task 1: Add Configuration Properties to `SAML2Properties`

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/config/SAML2Properties.java:46` (after last field)
- Modify: `src/main/resources/config/application-saml2.yml:49` (after `lang-key-pattern`)

- [ ] **Step 1: Add fields to SAML2Properties**

Add after line 46 (`private Set<ExtractionPattern> valueExtractionPatterns = Set.of();`):

```java
private List<String> allowedRedirectSchemes = List.of();

private boolean externalTokenRememberMe = false;
```

Add getters/setters after the existing ones (before the inner classes), following the existing JavaBean style:

```java
/**
 * Gets the allowed redirect URI schemes for external client authentication.
 *
 * @return the list of allowed schemes (e.g., "vscode", "artemis-ios")
 */
public List<String> getAllowedRedirectSchemes() {
    return allowedRedirectSchemes;
}

/**
 * Sets the allowed redirect URI schemes.
 *
 * @param allowedRedirectSchemes the allowed schemes
 */
public void setAllowedRedirectSchemes(List<String> allowedRedirectSchemes) {
    this.allowedRedirectSchemes = allowedRedirectSchemes;
}

/**
 * Gets whether external client tokens should use rememberMe (long-lived) validity.
 *
 * @return true if rememberMe validity should be used
 */
public boolean isExternalTokenRememberMe() {
    return externalTokenRememberMe;
}

/**
 * Sets whether external client tokens should use rememberMe validity.
 *
 * @param externalTokenRememberMe true for long-lived tokens
 */
public void setExternalTokenRememberMe(boolean externalTokenRememberMe) {
    this.externalTokenRememberMe = externalTokenRememberMe;
}
```

Also add the `List` import at the top (it may already be there — check first):

```java
import java.util.List;
```

- [ ] **Step 2: Add defaults to application-saml2.yml**

Add after line 48 (`lang-key-pattern: 'en'`) and before the `value-extraction-patterns:` block:

```yaml
    # Allowed URI schemes for external client redirect after SAML2 authentication.
    # Empty list (default) = feature disabled. If redirect_uri is provided while disabled, HTTP 400 is returned.
    # Example: ['vscode', 'artemis-ios', 'artemis-android']
    # WARNING: http and https schemes are always rejected regardless of this setting.
    allowed-redirect-schemes: []
    # Whether external client tokens should use long-lived (rememberMe) validity. Default: false.
    external-token-remember-me: false
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava -x webapp 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/core/config/SAML2Properties.java src/main/resources/config/application-saml2.yml
git commit -m "General: Add SAML2 external redirect configuration properties"
```

---

### Task 2: Create `HazelcastSaml2RedirectUriRepository`

**Files:**
- Create: `src/main/java/de/tum/cit/aet/artemis/core/repository/saml2/HazelcastSaml2RedirectUriRepository.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/core/repository/saml2/HazelcastSaml2RedirectUriRepositoryTest.java`

- [ ] **Step 1: Write the unit test**

```java
package de.tum.cit.aet.artemis.core.repository.saml2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

class HazelcastSaml2RedirectUriRepositoryTest extends AbstractSpringIntegrationLocalVCSamlTest {

    @Autowired
    private HazelcastSaml2RedirectUriRepository repository;

    private static final String TEST_NONCE = "test-nonce-123";

    private static final String TEST_REDIRECT_URI = "vscode://artemis/callback";

    @AfterEach
    void cleanup() {
        repository.consumeAndRemove(TEST_NONCE);
    }

    @Test
    void testSaveAndConsume() {
        repository.save(TEST_NONCE, TEST_REDIRECT_URI);
        String result = repository.consumeAndRemove(TEST_NONCE);
        assertThat(result).isEqualTo(TEST_REDIRECT_URI);
    }

    @Test
    void testConsumeRemovesEntry() {
        repository.save(TEST_NONCE, TEST_REDIRECT_URI);
        repository.consumeAndRemove(TEST_NONCE);
        String result = repository.consumeAndRemove(TEST_NONCE);
        assertThat(result).isNull();
    }

    @Test
    void testConsumeNonExistentNonce() {
        String result = repository.consumeAndRemove("nonexistent-nonce");
        assertThat(result).isNull();
    }

    @Test
    void testMultipleNonces() {
        String nonce1 = "nonce-1";
        String nonce2 = "nonce-2";
        repository.save(nonce1, "vscode://callback1");
        repository.save(nonce2, "vscode://callback2");

        assertThat(repository.consumeAndRemove(nonce1)).isEqualTo("vscode://callback1");
        assertThat(repository.consumeAndRemove(nonce2)).isEqualTo("vscode://callback2");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests HazelcastSaml2RedirectUriRepositoryTest -x webapp 2>&1 | tail -10`
Expected: FAIL — class `HazelcastSaml2RedirectUriRepository` does not exist

- [ ] **Step 3: Write the implementation**

```java
package de.tum.cit.aet.artemis.core.repository.saml2;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SAML2;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * Hazelcast-backed store for SAML2 redirect URI nonces.
 * <p>
 * Stores validated redirect_uri values keyed by UUID nonce during the SAML2 authentication flow.
 * Nonces are one-time use (atomically consumed on lookup) and expire after 5 minutes via Hazelcast TTL.
 * <p>
 * This distributed store ensures the feature works in clustered Artemis deployments where
 * the SAML2 AuthnRequest and Response may be handled by different nodes.
 */
@Profile(PROFILE_SAML2)
@Lazy
@Repository
public class HazelcastSaml2RedirectUriRepository {

    private static final Logger log = LoggerFactory.getLogger(HazelcastSaml2RedirectUriRepository.class);

    private static final String MAP_NAME = "saml2-redirect-uri-nonce-map";

    private static final int NONCE_TTL_SECONDS = 300; // 5 minutes

    private final HazelcastInstance hazelcastInstance;

    @Nullable
    private IMap<String, String> nonceMap;

    /**
     * Constructs the repository.
     *
     * @param hazelcastInstance the Hazelcast cluster instance
     */
    public HazelcastSaml2RedirectUriRepository(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Initializes the Hazelcast map with a TTL of 5 minutes.
     */
    @PostConstruct
    public void init() {
        MapConfig mapConfig = hazelcastInstance.getConfig().getMapConfig(MAP_NAME);
        mapConfig.setTimeToLiveSeconds(NONCE_TTL_SECONDS);
    }

    private IMap<String, String> getNonceMap() {
        if (this.nonceMap == null) {
            this.nonceMap = hazelcastInstance.getMap(MAP_NAME);
        }
        return this.nonceMap;
    }

    /**
     * Stores a nonce → redirect_uri mapping.
     *
     * @param nonce       the UUID nonce (used as RelayState)
     * @param redirectUri the validated redirect URI
     */
    public void save(String nonce, String redirectUri) {
        getNonceMap().put(nonce, redirectUri);
        log.debug("Saved SAML2 redirect nonce: {}", nonce);
    }

    /**
     * Atomically retrieves and removes the redirect_uri for the given nonce.
     * Returns null if the nonce does not exist or has expired.
     *
     * @param nonce the UUID nonce from RelayState
     * @return the redirect_uri, or null if not found/expired/already consumed
     */
    @Nullable
    public String consumeAndRemove(String nonce) {
        String redirectUri = getNonceMap().remove(nonce);
        if (redirectUri != null) {
            log.debug("Consumed SAML2 redirect nonce: {}", nonce);
        }
        return redirectUri;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests HazelcastSaml2RedirectUriRepositoryTest -x webapp 2>&1 | tee /tmp/test_hazelcast_saml2.txt | tail -10`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/core/repository/saml2/HazelcastSaml2RedirectUriRepository.java src/test/java/de/tum/cit/aet/artemis/core/repository/saml2/HazelcastSaml2RedirectUriRepositoryTest.java
git commit -m "General: Add Hazelcast-backed SAML2 redirect URI nonce repository"
```

---

### Task 3: Create `SAML2RedirectUriValidator`

**Files:**
- Create: `src/main/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2RedirectUriValidator.java`
- Create: `src/test/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2RedirectUriValidatorTest.java`

- [ ] **Step 1: Write the unit tests**

```java
package de.tum.cit.aet.artemis.core.security.saml2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SAML2RedirectUriValidatorTest {

    private SAML2RedirectUriValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SAML2RedirectUriValidator(List.of("vscode", "artemis-ios"));
    }

    @Test
    void testValidVscodeUri() {
        assertThat(validator.validate("vscode://artemis/callback")).isEmpty();
    }

    @Test
    void testValidUriWithQueryParams() {
        assertThat(validator.validate("vscode://artemis/callback?state=abc")).isEmpty();
    }

    @Test
    void testRejectHttpScheme() {
        assertThat(validator.validate("http://evil.com/steal")).isPresent();
    }

    @Test
    void testRejectHttpsScheme() {
        assertThat(validator.validate("https://evil.com/steal")).isPresent();
    }

    @Test
    void testRejectUnknownScheme() {
        assertThat(validator.validate("evil-scheme://callback")).isPresent();
    }

    @Test
    void testRejectRelativeUri() {
        assertThat(validator.validate("/relative/path")).isPresent();
    }

    @Test
    void testRejectFragment() {
        assertThat(validator.validate("vscode://callback#fragment")).isPresent();
    }

    @Test
    void testRejectTooLong() {
        String longUri = "vscode://artemis/" + "a".repeat(200);
        assertThat(validator.validate(longUri)).isPresent();
    }

    @Test
    void testRejectMalformedUri() {
        assertThat(validator.validate("://not-a-uri")).isPresent();
    }

    @Test
    void testRejectEmptyString() {
        assertThat(validator.validate("")).isPresent();
    }

    @Test
    void testCaseInsensitiveScheme() {
        assertThat(validator.validate("VSCODE://artemis/callback")).isEmpty();
    }

    @Test
    void testHttpBlockedEvenIfInAllowlist() {
        var permissiveValidator = new SAML2RedirectUriValidator(List.of("http", "https", "vscode"));
        assertThat(permissiveValidator.validate("http://evil.com")).isPresent();
        assertThat(permissiveValidator.validate("https://evil.com")).isPresent();
        assertThat(permissiveValidator.validate("vscode://callback")).isEmpty();
    }

    @Test
    void testEmptyAllowlistRejectsEverything() {
        var emptyValidator = new SAML2RedirectUriValidator(List.of());
        assertThat(emptyValidator.validate("vscode://callback")).isPresent();
    }

    @Test
    void testFeatureDisabledCheck() {
        var emptyValidator = new SAML2RedirectUriValidator(List.of());
        assertThat(emptyValidator.isFeatureEnabled()).isFalse();

        assertThat(validator.isFeatureEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests SAML2RedirectUriValidatorTest -x webapp 2>&1 | tail -10`
Expected: FAIL — class does not exist

- [ ] **Step 3: Write the implementation**

```java
package de.tum.cit.aet.artemis.core.security.saml2;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Validates redirect URIs for the SAML2 external client authentication flow.
 * <p>
 * Only custom URI schemes configured in the allowlist are accepted.
 * {@code http} and {@code https} are always rejected regardless of configuration.
 */
public class SAML2RedirectUriValidator {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("http", "https");

    private static final int MAX_URI_BYTES = 200;

    private final List<String> allowedSchemes;

    /**
     * Constructs a validator with the given allowed schemes.
     *
     * @param allowedSchemes the list of allowed URI schemes (e.g., "vscode", "artemis-ios")
     */
    public SAML2RedirectUriValidator(List<String> allowedSchemes) {
        this.allowedSchemes = allowedSchemes.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * Whether the external redirect feature is enabled (non-empty allowlist).
     *
     * @return true if at least one scheme is configured
     */
    public boolean isFeatureEnabled() {
        return !allowedSchemes.isEmpty();
    }

    /**
     * Validates a redirect URI against the configured allowlist and security rules.
     *
     * @param redirectUri the redirect URI to validate
     * @return empty if valid, or a rejection reason string
     */
    public Optional<String> validate(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return Optional.of("redirect_uri is empty");
        }

        if (redirectUri.getBytes(StandardCharsets.UTF_8).length > MAX_URI_BYTES) {
            return Optional.of("redirect_uri exceeds maximum length of " + MAX_URI_BYTES + " bytes");
        }

        URI uri;
        try {
            uri = URI.create(redirectUri);
        }
        catch (IllegalArgumentException e) {
            return Optional.of("redirect_uri is not a valid URI: " + e.getMessage());
        }

        if (!uri.isAbsolute()) {
            return Optional.of("redirect_uri must be an absolute URI");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);

        if (BLOCKED_SCHEMES.contains(scheme)) {
            return Optional.of("http/https redirect URIs are not allowed");
        }

        if (!allowedSchemes.contains(scheme)) {
            return Optional.of("URI scheme '" + scheme + "' is not in the allowlist");
        }

        if (uri.getFragment() != null) {
            return Optional.of("redirect_uri must not contain a fragment");
        }

        return Optional.empty();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests SAML2RedirectUriValidatorTest -x webapp 2>&1 | tee /tmp/test_validator.txt | tail -10`
Expected: All 14 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2RedirectUriValidator.java src/test/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2RedirectUriValidatorTest.java
git commit -m "General: Add SAML2 redirect URI validator"
```

---

### Task 4: Create `SAML2ExternalClientAuthenticationSuccessHandler`

**Files:**
- Create: `src/main/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2ExternalClientAuthenticationSuccessHandler.java`

- [ ] **Step 1: Write the implementation**

```java
package de.tum.cit.aet.artemis.core.security.saml2;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;
import de.tum.cit.aet.artemis.core.security.UserNotActivatedException;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import java.time.Instant;
import java.util.Map;

/**
 * Authentication success handler for SAML2 that supports external client redirect.
 * <p>
 * If a nonce is found in RelayState, the handler looks up the validated redirect_uri from
 * Hazelcast, mints a JWT, and redirects to the external client URI with the token.
 * If no nonce is present, it falls back to the default behavior (redirect to "/").
 */
public class SAML2ExternalClientAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SAML2ExternalClientAuthenticationSuccessHandler.class);

    private final HazelcastSaml2RedirectUriRepository redirectUriRepository;

    private final SAML2Service saml2Service;

    private final TokenProvider tokenProvider;

    private final AuditEventRepository auditEventRepository;

    private final boolean externalTokenRememberMe;

    /**
     * Constructs the handler.
     *
     * @param redirectUriRepository   Hazelcast nonce store
     * @param saml2Service            SAML2 user handling service
     * @param tokenProvider           JWT token provider
     * @param auditEventRepository    audit event repository
     * @param externalTokenRememberMe whether to use long-lived tokens for external clients
     */
    public SAML2ExternalClientAuthenticationSuccessHandler(HazelcastSaml2RedirectUriRepository redirectUriRepository, SAML2Service saml2Service, TokenProvider tokenProvider,
            AuditEventRepository auditEventRepository, boolean externalTokenRememberMe) {
        super("/");
        setAlwaysUseDefaultTargetUrl(true);
        this.redirectUriRepository = redirectUriRepository;
        this.saml2Service = saml2Service;
        this.tokenProvider = tokenProvider;
        this.auditEventRepository = auditEventRepository;
        this.externalTokenRememberMe = externalTokenRememberMe;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String relayState = request.getParameter("RelayState");

        if (relayState == null || relayState.isBlank()) {
            // No nonce — standard web flow: redirect to "/" and let SPA handle JWT exchange
            log.debug("No RelayState nonce, falling back to default SAML2 redirect");
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        // External client flow: consume nonce from Hazelcast
        String redirectUri = redirectUriRepository.consumeAndRemove(relayState);
        if (redirectUri == null) {
            log.warn("SAML2 redirect nonce not found or expired: {}", relayState);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or expired redirect nonce");
            return;
        }

        // Extract principal from Saml2Authentication
        if (!(authentication instanceof Saml2Authentication saml2Auth) || !(saml2Auth.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal)) {
            log.error("SAML2 authentication success but principal is not Saml2AuthenticatedPrincipal");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected authentication type");
            return;
        }

        // Reuse SAML2Service for user creation/update, audit logging, login email
        Authentication processedAuth;
        try {
            processedAuth = saml2Service.handleAuthentication(authentication, principal, request);
        }
        catch (UserNotActivatedException e) {
            log.debug("SAML2 external redirect denied: user not activated");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
        }

        // Generate JWT from the processed authentication (UsernamePasswordAuthenticationToken)
        String jwt = tokenProvider.createToken(processedAuth, externalTokenRememberMe);

        // Build redirect URI with JWT parameter
        String targetUri = UriComponentsBuilder.fromUriString(redirectUri).queryParam("jwt", jwt).build().toUriString();

        // Audit log (without JWT in URI)
        auditEventRepository.add(new AuditEvent(Instant.now(), processedAuth.getName(), "SAML2_EXTERNAL_REDIRECT_SUCCESS",
                Map.of("redirectScheme", URI.create(redirectUri).getScheme())));

        log.info("SAML2 external redirect for user '{}' to scheme '{}'", processedAuth.getName(), URI.create(redirectUri).getScheme());

        response.sendRedirect(targetUri);
    }
}
```

Add the missing `URI` import at the top:

```java
import java.net.URI;
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava -x webapp 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/core/security/saml2/SAML2ExternalClientAuthenticationSuccessHandler.java
git commit -m "General: Add SAML2 external client authentication success handler"
```

---

### Task 5: Wire Everything in `SAML2Configuration`

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/config/SAML2Configuration.java`

- [ ] **Step 1: Modify SAML2Configuration**

Replace the entire file content with:

```java
package de.tum.cit.aet.artemis.core.config;

import java.security.Security;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationRequestResolver;
import org.springframework.security.saml2.provider.service.metadata.OpenSaml5MetadataResolver;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.Saml2MetadataFilter;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.boot.actuate.audit.AuditEventRepository;

import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2ExternalClientAuthenticationSuccessHandler;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2RedirectUriValidator;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;

/**
 * Describes the security configuration for SAML2.
 */
@Configuration
@Lazy
@Profile(Constants.PROFILE_SAML2)
public class SAML2Configuration {

    private static final Logger log = LoggerFactory.getLogger(SAML2Configuration.class);

    private final SAML2Properties saml2Properties;

    private final SAML2Service saml2Service;

    private final TokenProvider tokenProvider;

    private final HazelcastSaml2RedirectUriRepository redirectUriRepository;

    private final AuditEventRepository auditEventRepository;

    /**
     * Constructs a new instance.
     *
     * @param saml2Properties       SAML2 configuration properties
     * @param saml2Service          SAML2 user handling service
     * @param tokenProvider         JWT token provider
     * @param redirectUriRepository Hazelcast nonce store
     * @param auditEventRepository  audit event repository
     */
    public SAML2Configuration(SAML2Properties saml2Properties, SAML2Service saml2Service, TokenProvider tokenProvider,
            HazelcastSaml2RedirectUriRepository redirectUriRepository, AuditEventRepository auditEventRepository) {
        // SAML2 / Shibboleth uses several algorithms that are provided by BouncyCastle
        Security.addProvider(new BouncyCastleProvider());
        this.saml2Properties = saml2Properties;
        this.saml2Service = saml2Service;
        this.tokenProvider = tokenProvider;
        this.redirectUriRepository = redirectUriRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Returns the RelyingPartyRegistrationRepository used by SAML2 configuration.
     * <p>
     * The relying parties are configured in the SAML2 properties. A helper method
     * {@link RelyingPartyRegistrations#fromMetadataLocation} extracts the needed information from the given
     * XML metadata file. Optionally X509 Credentials can be supplied to enable encryption.
     *
     * @return the RelyingPartyRegistrationRepository used by SAML2 configuration.
     */
    @Bean
    RelyingPartyRegistrationResolver relyingPartyRegistrationResolver(RelyingPartyRegistrationRepository registrations) {
        return new DefaultRelyingPartyRegistrationResolver(registrations);
    }

    @Bean
    FilterRegistrationBean<Saml2MetadataFilter> metadata(RelyingPartyRegistrationResolver registrations) {
        Saml2MetadataFilter metadata = new Saml2MetadataFilter(registrations, new OpenSaml5MetadataResolver());
        FilterRegistrationBean<Saml2MetadataFilter> filter = new FilterRegistrationBean<>(metadata);
        filter.setOrder(-101);
        return filter;
    }

    /**
     * Since this configuration is annotated with {@link Order} and {@link SecurityConfiguration}
     * is not, this configuration is evaluated first when the SAML2 Profile is active.
     *
     * @param http          The Spring http security configurer.
     * @param registrations The relying party registration resolver.
     * @return The configured http security filter chain.
     * @throws Exception Thrown in case Spring detects an issue with the security configuration.
     */
    @Bean
    @Order(1)
    protected SecurityFilterChain saml2FilterChain(final HttpSecurity http, RelyingPartyRegistrationResolver registrations) throws Exception {
        SAML2RedirectUriValidator validator = new SAML2RedirectUriValidator(saml2Properties.getAllowedRedirectSchemes());

        // Configure authentication request resolver with optional redirect_uri support
        OpenSaml5AuthenticationRequestResolver authRequestResolver = new OpenSaml5AuthenticationRequestResolver(registrations);
        authRequestResolver.setRelayStateResolver(request -> resolveRelayState(request, validator));

        // Configure success handler
        SAML2ExternalClientAuthenticationSuccessHandler successHandler = new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider,
                auditEventRepository, saml2Properties.isExternalTokenRememberMe());

        // @formatter:off
        http
            // This filter chain is only applied if the URL matches
            // Else the request is filtered by {@link SecurityConfiguration}.
            .securityMatcher("/api/core/public/saml2", "/saml2/**", "/login/saml2/**")
            // Needed for SAML to work properly
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // The request to the api is permitted and checked directly
                // This allows returning a 401 if the user is not logged in via SAML2
                // to notify the client that a login is needed.
                .requestMatchers("/api/core/public/saml2").permitAll()
                // Every other request must be authenticated. Any request triggers a SAML2
                // authentication flow
                .anyRequest().authenticated()
            )
            // Processes the RelyingPartyRegistrationRepository Bean and installs the filters for SAML2
            .saml2Login(config -> config
                .authenticationRequestResolver(authRequestResolver)
                .successHandler(successHandler)
            );
        // @formatter:on

        return http.build();
    }

    /**
     * Resolves the RelayState for the SAML2 AuthnRequest.
     * If a valid redirect_uri is provided, stores it in Hazelcast and returns a nonce.
     *
     * @param request   the HTTP request initiating the SAML2 flow
     * @param validator the redirect URI validator
     * @return the RelayState value (nonce or null)
     */
    private String resolveRelayState(HttpServletRequest request, SAML2RedirectUriValidator validator) {
        String redirectUri = request.getParameter("redirect_uri");
        if (redirectUri == null || redirectUri.isBlank()) {
            return null;
        }

        if (!validator.isFeatureEnabled()) {
            log.warn("SAML2 redirect_uri provided but feature is disabled (empty allowlist). "
                    + "redirect_uri will be ignored and user will be redirected to '/'.");
            // NOTE: Cannot return HTTP 400 from RelayState resolver (no access to HttpServletResponse).
            // The nonce won't be stored, so no external redirect will occur.
            // The user will go through the normal SAML2 web flow instead.
            return null;
        }

        Optional<String> rejection = validator.validate(redirectUri);
        if (rejection.isPresent()) {
            log.warn("SAML2 redirect_uri rejected: {}", rejection.get());
            return null;
        }

        String nonce = UUID.randomUUID().toString();
        redirectUriRepository.save(nonce, redirectUri);
        log.debug("SAML2 redirect_uri stored with nonce: {}", nonce);
        return nonce;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava -x webapp 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Verify existing SAML2 tests still pass**

Run: `./gradlew test --tests UserSaml2IntegrationTest -x webapp 2>&1 | tee /tmp/test_saml2_existing.txt | tail -10`
Expected: All existing tests PASS (no regression)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/core/config/SAML2Configuration.java
git commit -m "General: Wire SAML2 external redirect in security configuration"
```

---

### Task 6: Integration Tests for External Redirect Flow

**Files:**
- Create: `src/test/java/de/tum/cit/aet/artemis/core/authentication/Saml2ExternalRedirectIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

```java
package de.tum.cit.aet.artemis.core.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2ExternalClientAuthenticationSuccessHandler;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Integration tests for SAML2 external client redirect flow.
 */
class Saml2ExternalRedirectIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    private static final String STUDENT_NAME = "student_external_redirect_test";

    @Autowired
    private HazelcastSaml2RedirectUriRepository redirectUriRepository;

    @Autowired
    private SAML2Service saml2Service;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @AfterEach
    void cleanup() {
        userTestRepository.findOneByLogin(STUDENT_NAME).ifPresent(userTestRepository::delete);
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void testExternalRedirectWithValidNonce() throws Exception {
        // Setup: store nonce in Hazelcast
        String nonce = "test-nonce-valid";
        String redirectUri = "vscode://artemis/callback";
        redirectUriRepository.save(nonce, redirectUri);

        // Create handler
        var handler = new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider, auditEventRepository, false);

        // Mock request with RelayState
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("RelayState", nonce);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Create SAML2 authentication
        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        handler.onAuthenticationSuccess(request, response, authentication);

        // Verify redirect to vscode:// with jwt parameter
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);
        String location = response.getRedirectedUrl();
        assertThat(location).startsWith("vscode://artemis/callback?jwt=");
        assertThat(location).contains("jwt=ey"); // JWT starts with "ey"
    }

    @Test
    void testFallbackWithoutRelayState() throws Exception {
        var handler = new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider, auditEventRepository, false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        // No RelayState parameter
        MockHttpServletResponse response = new MockHttpServletResponse();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        handler.onAuthenticationSuccess(request, response, authentication);

        // Verify fallback to "/"
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void testExpiredNonceReturns400() throws Exception {
        var handler = new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider, auditEventRepository, false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("RelayState", "nonexistent-nonce");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void testConsumedNonceReturns400OnReplay() throws Exception {
        String nonce = "test-nonce-replay";
        redirectUriRepository.save(nonce, "vscode://artemis/callback");

        var handler = new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider, auditEventRepository, false);

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        // First call — succeeds
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setParameter("RelayState", nonce);
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request1, response1, authentication);
        assertThat(response1.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);

        // Second call — nonce consumed, should fail
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setParameter("RelayState", nonce);
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request2, response2, authentication);
        assertThat(response2.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void testRedirectUriWithExistingQueryParams() throws Exception {
        String nonce = "test-nonce-query";
        redirectUriRepository.save(nonce, "vscode://artemis/callback?state=abc");

        var handler = new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider, auditEventRepository, false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("RelayState", nonce);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        handler.onAuthenticationSuccess(request, response, authentication);

        String location = response.getRedirectedUrl();
        assertThat(location).startsWith("vscode://artemis/callback?state=abc&jwt=");
    }

    @Test
    void testExistingSaml2WebFlowUnchanged() throws Exception {
        // Verify the existing POST /api/core/public/saml2 endpoint still works
        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, org.springframework.http.HttpStatus.OK);

        // User should be created
        assertThat(userTestRepository.findOneByLogin(STUDENT_NAME)).isPresent();
    }

    private Saml2AuthenticatedPrincipal createPrincipal() {
        Map<String, List<Object>> attributes = new HashMap<>();
        attributes.put("uid", List.of(STUDENT_NAME));
        attributes.put("first_name", List.of("External"));
        attributes.put("last_name", List.of("User"));
        attributes.put("email", List.of(STUDENT_NAME + "@test.invalid"));
        attributes.put("registration_number", List.of("EXT123"));
        return new DefaultSaml2AuthenticatedPrincipal(STUDENT_NAME, attributes);
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `./gradlew test --tests Saml2ExternalRedirectIntegrationTest -x webapp 2>&1 | tee /tmp/test_saml2_redirect.txt | tail -15`
Expected: All 6 tests PASS

- [ ] **Step 3: Verify ALL existing SAML2 tests still pass**

Run: `./gradlew test --tests UserSaml2IntegrationTest --tests PasskeySaml2IntegrationTest -x webapp 2>&1 | tee /tmp/test_saml2_all.txt | tail -10`
Expected: All existing tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/de/tum/cit/aet/artemis/core/authentication/Saml2ExternalRedirectIntegrationTest.java
git commit -m "General: Add integration tests for SAML2 external client redirect"
```

---

### Task 7: Linting & Final Verification

**Files:** All files modified/created in Tasks 1-6

- [ ] **Step 1: Run Spotless**

Run: `./gradlew spotlessCheck -x webapp 2>&1 | tail -10`
If it fails: `./gradlew spotlessApply -x webapp`

- [ ] **Step 2: Run Checkstyle**

Run: `./gradlew checkstyleMain -x webapp 2>&1 | tail -10`
Expected: No violations

- [ ] **Step 3: Run all SAML2 tests one final time**

Run: `./gradlew test --tests "*Saml2*" -x webapp 2>&1 | tee /tmp/test_saml2_final.txt | tail -15`
Expected: All tests PASS

- [ ] **Step 4: Fix any issues and commit**

```bash
git add -u
git commit -m "General: Fix formatting for SAML2 external redirect feature"
```
