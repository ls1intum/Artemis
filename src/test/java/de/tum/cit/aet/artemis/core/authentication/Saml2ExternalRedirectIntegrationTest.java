package de.tum.cit.aet.artemis.core.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2ExternalClientAuthenticationSuccessHandler;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2RedirectUriValidator;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

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

    private final Set<String> createdNonces = new HashSet<>();

    @AfterEach
    void cleanup() {
        createdNonces.forEach(redirectUriRepository::consumeAndRemove);
        createdNonces.clear();
        userTestRepository.findOneByLogin(STUDENT_NAME).ifPresent(userTestRepository::delete);
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void testExternalRedirectWithValidNonce() throws Exception {
        String nonce = "test-nonce-valid";
        storeNonce(nonce, "vscode://artemis/callback");

        var handler = createHandler();
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        MockHttpServletResponse mockResponse = onSuccess(handler, nonce, authentication);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);
        String location = mockResponse.getRedirectedUrl();
        assertThat(location).startsWith("vscode://artemis/callback?jwt=");
        assertThat(location).contains("jwt=ey"); // JWT starts with "ey"
    }

    @Test
    void testFallbackWithoutRelayState() throws Exception {
        var handler = createHandler();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(mockRequest, mockResponse, createAuthentication());

        assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void testUnknownNonceReturns400() throws Exception {
        var handler = createHandler();
        MockHttpServletResponse mockResponse = onSuccess(handler, "nonexistent-nonce", createAuthentication());

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void testConsumedNonceReturns400OnReplay() throws Exception {
        String nonce = "test-nonce-replay";
        storeNonce(nonce, "vscode://artemis/callback");

        var handler = createHandler();
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        // First call — succeeds
        MockHttpServletResponse response1 = onSuccess(handler, nonce, authentication);
        assertThat(response1.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);

        // Second call — nonce consumed, should fail
        TestSecurityContextHolder.setAuthentication(authentication);
        MockHttpServletResponse response2 = onSuccess(handler, nonce, authentication);
        assertThat(response2.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void testTokenLifetimeMatchesConfiguredTtl() throws Exception {
        String nonce = "test-nonce-ttl";
        storeNonce(nonce, "vscode://artemis/callback");

        var handler = createHandler(Duration.ofMinutes(10));
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        Instant before = Instant.now();
        MockHttpServletResponse mockResponse = onSuccess(handler, nonce, authentication);

        String location = mockResponse.getRedirectedUrl();
        assertThat(location).isNotNull().contains("jwt=");
        String jwt = location.substring(location.indexOf("jwt=") + "jwt=".length());

        Instant expiration = tokenProvider.getExpirationDate(jwt).toInstant();
        // Lower bound: the token must not be valid for the full default 24h.
        assertThat(expiration).isBefore(before.plus(Duration.ofHours(1)));
        // Upper bound: must be at least the requested 10 minutes from "before".
        assertThat(expiration).isAfterOrEqualTo(before.plus(Duration.ofMinutes(10)).minusSeconds(2));
    }

    @Test
    void testRedirectUriWithExistingQueryParams() throws Exception {
        String nonce = "test-nonce-query";
        storeNonce(nonce, "vscode://artemis/callback?state=abc");

        var handler = createHandler();
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        MockHttpServletResponse mockResponse = onSuccess(handler, nonce, authentication);

        assertThat(mockResponse.getRedirectedUrl()).startsWith("vscode://artemis/callback?state=abc&jwt=");
    }

    @Test
    void testRedirectUriWithExistingJwtParamIsReplaced() throws Exception {
        String nonce = "test-nonce-jwt-replace";
        storeNonce(nonce, "vscode://artemis/callback?jwt=attacker-token");

        var handler = createHandler();
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        MockHttpServletResponse mockResponse = onSuccess(handler, nonce, authentication);

        String location = mockResponse.getRedirectedUrl();
        assertThat(location).doesNotContain("attacker-token").contains("jwt=ey");
        // Exactly one jwt parameter — the attacker-supplied one was replaced, not appended.
        assertThat(location.indexOf("jwt=")).isEqualTo(location.lastIndexOf("jwt="));
    }

    @Test
    void testStoredRedirectUriFailingRevalidationReturns400() throws Exception {
        // The handler's validator only allows the "vscode" scheme; a stored "artemis-ios" URI must be rejected.
        String nonce = "test-nonce-revalidation";
        storeNonce(nonce, "artemis-ios://artemis/callback");

        var handler = createHandler();
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        MockHttpServletResponse mockResponse = onSuccess(handler, nonce, authentication);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        // handleAuthentication must not have run — no user was created.
        assertThat(userTestRepository.findOneByLogin(STUDENT_NAME)).isEmpty();
    }

    @Test
    void testPrincipalTypeMismatchReturns500() throws Exception {
        String nonce = "test-nonce-principal-mismatch";
        storeNonce(nonce, "vscode://artemis/callback");

        var handler = createHandler();
        Authentication nonSamlAuthentication = new TestingAuthenticationToken("some-user", "credentials");

        MockHttpServletResponse mockResponse = onSuccess(handler, nonce, nonSamlAuthentication);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void testSuccessWritesAuditEventWithoutJwt() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        String nonce = "test-nonce-audit";
        storeNonce(nonce, "vscode://artemis/callback");

        var handler = createHandler();
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        onSuccess(handler, nonce, authentication);

        List<AuditEvent> events = auditEventRepository.find(STUDENT_NAME, before, AuditEventConstants.SAML2_EXTERNAL_REDIRECT_SUCCESS);
        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getData()).containsEntry("redirectScheme", "vscode");
            assertThat(event.getData()).doesNotContainKey("jwt");
        });
    }

    @Test
    void testInactiveUserReturns403() throws Exception {
        var handler = createHandler();
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        // First create the user via a successful redirect
        String nonce = "test-nonce-inactive";
        storeNonce(nonce, "vscode://artemis/callback");
        MockHttpServletResponse mockResponse = onSuccess(handler, nonce, authentication);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);

        // Now deactivate the user
        var user = userTestRepository.findOneByLogin(STUDENT_NAME).orElseThrow();
        user.setActivated(false);
        userTestRepository.saveAndFlush(user);

        // Try again — should get 403
        String nonce2 = "test-nonce-inactive-2";
        storeNonce(nonce2, "vscode://artemis/callback");
        TestSecurityContextHolder.setAuthentication(authentication);
        MockHttpServletResponse mockResponse2 = onSuccess(handler, nonce2, authentication);

        assertThat(mockResponse2.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void testExistingSaml2WebFlowUnchanged() throws Exception {
        Saml2Authentication authentication = createAuthentication();
        TestSecurityContextHolder.setAuthentication(authentication);

        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, org.springframework.http.HttpStatus.OK);

        assertThat(userTestRepository.findOneByLogin(STUDENT_NAME)).isPresent();
    }

    private void storeNonce(String nonce, String redirectUri) {
        redirectUriRepository.save(nonce, redirectUri);
        createdNonces.add(nonce);
    }

    private MockHttpServletResponse onSuccess(SAML2ExternalClientAuthenticationSuccessHandler handler, String relayState, Authentication authentication) throws Exception {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter("RelayState", relayState);
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(mockRequest, mockResponse, authentication);
        return mockResponse;
    }

    private SAML2ExternalClientAuthenticationSuccessHandler createHandler() {
        return createHandler(Duration.ofMinutes(60));
    }

    private SAML2ExternalClientAuthenticationSuccessHandler createHandler(Duration tokenTtl) {
        SAML2RedirectUriValidator validator = new SAML2RedirectUriValidator(List.of("vscode"));
        return new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider, auditEventRepository, validator, tokenTtl);
    }

    private Saml2Authentication createAuthentication() {
        return new Saml2Authentication(createPrincipal(), "credentials", null);
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
