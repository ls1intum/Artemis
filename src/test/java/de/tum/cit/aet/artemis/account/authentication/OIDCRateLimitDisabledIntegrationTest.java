package de.tum.cit.aet.artemis.account.authentication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Integration tests for OIDC rate limiting feature toggle when disabled.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = { "artemis.rate-limiting.enabled=false", "artemis.rate-limiting.oidc-authorization-requests-per-minute=1" })
class OIDCRateLimitDisabledIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    @Test
    void testOidcAuthorization_FeatureToggleDisabled_ShouldBypassLimit() throws Exception {
        // If rate-limiting is disabled, no limits should be faced
        request.performMvcRequest(get("/oauth2/authorization/oidc")).andExpect(status().is3xxRedirection());

        request.performMvcRequest(get("/oauth2/authorization/oidc")).andExpect(status().is3xxRedirection());
    }
}
