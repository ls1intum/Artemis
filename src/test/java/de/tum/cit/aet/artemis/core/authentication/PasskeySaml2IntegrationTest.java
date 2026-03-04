package de.tum.cit.aet.artemis.core.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Tests that passkey (WebAuthn) authentication is not intercepted by the SAML2 security filter chain.
 * <p>
 * When the SAML2 profile is active, the SAML2 filter chain ({@code @Order(1)}) must only match
 * SAML2-specific paths ({@code /login/saml2/**}), not all {@code /login/**} paths.
 * Otherwise, {@code /login/webauthn} requests would be captured by the SAML2 chain and
 * redirected to the IdP instead of being processed by {@code ArtemisWebAuthnAuthenticationFilter}.
 */
class PasskeySaml2IntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    @Test
    void testWebAuthnEndpointIsNotInterceptedBySaml2FilterChain() throws Exception {
        // POST to /login/webauthn without a valid WebAuthn credential payload.
        // If the SAML2 filter chain incorrectly matches /login/webauthn, this would result in a
        // 302 redirect to the SAML2 IdP login page (because SAML2 chain has .anyRequest().authenticated()).
        // With the correct matcher (/login/saml2/**), the request reaches the main filter chain's
        // WebAuthn filter, which rejects it with 401 (no valid credential) instead of redirecting.
        MvcResult result = request.performMvcRequest(MockMvcRequestBuilders.post(new URI("/login/webauthn")).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        int statusCode = result.getResponse().getStatus();
        String redirectUrl = result.getResponse().getRedirectedUrl();

        // The request must NOT be redirected to a SAML2 IdP
        assertThat(statusCode).as("POST /login/webauthn should not be redirected to SAML2 IdP").isNotEqualTo(HttpStatus.FOUND.value());
        if (redirectUrl != null) {
            assertThat(redirectUrl).as("POST /login/webauthn must not redirect to SAML2 endpoints").doesNotContain("saml2");
        }

        // The WebAuthn filter should handle the request and return 401 (invalid credentials)
        // or 400 (malformed request), but NOT a redirect
        assertThat(statusCode).as("POST /login/webauthn should be handled by WebAuthn filter, not SAML2 chain").isIn(HttpStatus.BAD_REQUEST.value(),
                HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    @Test
    void testSaml2EndpointsStillWork() throws Exception {
        // Verify that the SAML2 chain still correctly handles its own endpoints
        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);
    }
}
