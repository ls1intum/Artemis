package de.tum.cit.aet.artemis.core.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.saml2.SAML2ExternalClientAuthenticationSuccessHandler;
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

    @AfterEach
    void cleanup() {
        userTestRepository.findOneByLogin(STUDENT_NAME).ifPresent(userTestRepository::delete);
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void testExternalRedirectWithValidNonce() throws Exception {
        String nonce = "test-nonce-valid";
        String redirectUri = "vscode://artemis/callback";
        redirectUriRepository.save(nonce, redirectUri);

        var handler = createHandler();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter("RelayState", nonce);
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        handler.onAuthenticationSuccess(mockRequest, mockResponse, authentication);

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

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        handler.onAuthenticationSuccess(mockRequest, mockResponse, authentication);

        assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void testExpiredNonceReturns400() throws Exception {
        var handler = createHandler();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter("RelayState", "nonexistent-nonce");
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        handler.onAuthenticationSuccess(mockRequest, mockResponse, authentication);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void testConsumedNonceReturns400OnReplay() throws Exception {
        String nonce = "test-nonce-replay";
        redirectUriRepository.save(nonce, "vscode://artemis/callback");

        var handler = createHandler();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);

        // First call — succeeds
        TestSecurityContextHolder.setAuthentication(authentication);
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setParameter("RelayState", nonce);
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request1, response1, authentication);
        assertThat(response1.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);

        // Second call — nonce consumed, should fail
        TestSecurityContextHolder.setAuthentication(authentication);
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

        var handler = createHandler();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter("RelayState", nonce);
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        handler.onAuthenticationSuccess(mockRequest, mockResponse, authentication);

        String location = mockResponse.getRedirectedUrl();
        assertThat(location).startsWith("vscode://artemis/callback?state=abc&jwt=");
    }

    @Test
    void testInactiveUserReturns403() throws Exception {
        // First create the user via a successful redirect
        String nonce = "test-nonce-inactive";
        redirectUriRepository.save(nonce, "vscode://artemis/callback");

        var handler = createHandler();

        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setParameter("RelayState", nonce);
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(mockRequest, mockResponse, authentication);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);

        // Now deactivate the user
        var user = userTestRepository.findOneByLogin(STUDENT_NAME).orElseThrow();
        user.setActivated(false);
        userTestRepository.saveAndFlush(user);

        // Try again — should get 403
        String nonce2 = "test-nonce-inactive-2";
        redirectUriRepository.save(nonce2, "vscode://artemis/callback");
        TestSecurityContextHolder.setAuthentication(authentication);

        MockHttpServletRequest mockRequest2 = new MockHttpServletRequest();
        mockRequest2.setParameter("RelayState", nonce2);
        MockHttpServletResponse mockResponse2 = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(mockRequest2, mockResponse2, authentication);

        assertThat(mockResponse2.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void testExistingSaml2WebFlowUnchanged() throws Exception {
        Saml2AuthenticatedPrincipal principal = createPrincipal();
        Saml2Authentication authentication = new Saml2Authentication(principal, "credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, org.springframework.http.HttpStatus.OK);

        assertThat(userTestRepository.findOneByLogin(STUDENT_NAME)).isPresent();
    }

    private SAML2ExternalClientAuthenticationSuccessHandler createHandler() {
        return new SAML2ExternalClientAuthenticationSuccessHandler(redirectUriRepository, saml2Service, tokenProvider, auditEventRepository, false);
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
