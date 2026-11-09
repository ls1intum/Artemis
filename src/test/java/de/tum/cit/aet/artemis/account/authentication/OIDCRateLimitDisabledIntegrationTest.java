package de.tum.cit.aet.artemis.account.authentication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Integration tests for OIDC rate limiting feature toggle when disabled.
 */
class OIDCRateLimitDisabledIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    @DynamicPropertySource
    static void rateLimitingProperties(DynamicPropertyRegistry registry) {
        registry.add("artemis.rate-limiting.enabled", () -> "false");
        registry.add("artemis.rate-limiting.oidc-authorization-requests-per-minute", () -> "1");
    }

    @Test
    void testOidcAuthorization_FeatureToggleDisabled_ShouldBypassLimit() throws Exception {
        // If rate-limiting is disabled, no limits should be faced
        request.performMvcRequest(get("/oauth2/authorization/oidc")).andExpect(status().is3xxRedirection());

        request.performMvcRequest(get("/oauth2/authorization/oidc")).andExpect(status().is3xxRedirection());
    }

    @Test
    void testOidcAuthorization_DisabledWithInvalidRemoteAddress_ShouldStillRedirect() throws Exception {
        request.performMvcRequest(get("/oauth2/authorization/oidc").with(req -> {
            req.setRemoteAddr("invalid-ip-address");
            return req;
        })).andExpect(status().is3xxRedirection());
    }
}
