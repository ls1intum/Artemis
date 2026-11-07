package de.tum.cit.aet.artemis.account.authentication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Integration tests for OIDC rate limiting functionality.
 * Verifies rate limits, exempt addresses, and feature toggles on the OIDC authorization endpoint.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = { "artemis.rate-limiting.enabled=true", "artemis.rate-limiting.oidc-authorization-requests-per-minute=1",
        "artemis.rate-limiting.exempt-addresses=192.168.1.100" })
class OIDCRateLimitIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    @Test
    void testOidcAuthorization_WithinLimit_ShouldSucceed() throws Exception {
        // First request should receive a redirect to the IdP (3xx redirect)
        request.performMvcRequest(get("/oauth2/authorization/oidc").with(req -> {
            req.setRemoteAddr("192.168.1.101");
            return req;
        })).andExpect(status().is3xxRedirection());
    }

    @Test
    void testOidcAuthorization_ExceedingLimit_ShouldReturn429() throws Exception {
        // 1. First request succeeds
        request.performMvcRequest(get("/oauth2/authorization/oidc").with(req -> {
            req.setRemoteAddr("192.168.1.102");
            return req;
        })).andExpect(status().is3xxRedirection());

        // 2. Second request -> 429 Too Many Requests
        request.performMvcRequest(get("/oauth2/authorization/oidc").with(req -> {
            req.setRemoteAddr("192.168.1.102");
            return req;
        })).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void testOidcAuthorization_ExemptAddress_ShouldBypassLimit() throws Exception {
        // Requests from exempt addresses should not affect the rate limit
        request.performMvcRequest(get("/oauth2/authorization/oidc").with(req -> {
            req.setRemoteAddr("192.168.1.100");
            return req;
        })).andExpect(status().is3xxRedirection());

        // Second request from exempt address succeeds
        request.performMvcRequest(get("/oauth2/authorization/oidc").with(req -> {
            req.setRemoteAddr("192.168.1.100");
            return req;
        })).andExpect(status().is3xxRedirection());
    }
}
