package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.account.config.OIDCConfiguration;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Integration tests ensuring that OIDC Configuration beans and Spring Security
 * filter chain wiring are properly set up when the OIDC feature toggle is enabled.
 */
@TestPropertySource(properties = "artemis.user-management.oidc.enabled=true")
class OIDCConfigurationIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    @Autowired(required = false)
    private OIDCConfiguration oidcConfiguration;

    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    void testOidcBeansAreInstantiatedWhenEnabled() {
        assertThat(oidcConfiguration).as("OIDCConfiguration bean must be initialized").isNotNull();
        assertThat(clientRegistrationRepository).as("ClientRegistrationRepository bean must be initialized").isNotNull();
    }

    @Test
    void testOidcFilterChainIsWiredCorrectlyForOidcEndpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/oidc");

        // Find a matching chain
        List<Filter> filters = filterChainProxy.getFilterChains().stream().filter(chain -> chain.matches(request)).flatMap(chain -> chain.getFilters().stream()).toList();

        assertThat(filters).as("Should wire filters for OIDC authorization path").isNotEmpty();

        boolean hasOAuth2Filter = filters.stream().anyMatch(filter -> filter.getClass().getName().contains("OAuth2") || filter.getClass().getName().contains("Oidc"));

        assertThat(hasOAuth2Filter).as("The security filter chain must contain OAuth2/OIDC processing filters").isTrue();
    }
}
