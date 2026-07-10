package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

import de.tum.cit.aet.artemis.account.config.OIDCConfiguration;
import de.tum.cit.aet.artemis.account.security.OIDCAuthenticationFailureHandler;
import de.tum.cit.aet.artemis.account.security.OIDCAuthenticationSuccessHandler;
import de.tum.cit.aet.artemis.account.security.OIDCService;

/**
 * Isolated configuration tests ensuring that OIDC beans and the custom SecurityFilterChain
 * are conditionally instantiated based on the feature toggle state.
 * This test avoids dirtying the heavy Spring context and bypasses Hazelcast initialization.
 */
class OIDCConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
            .withUserConfiguration(MockDependenciesConfiguration.class, OIDCConfiguration.class);

    @Configuration
    static class MockDependenciesConfiguration {

        @Bean
        OIDCService oidcService() {
            return Mockito.mock(OIDCService.class);
        }

        @Bean
        OIDCAuthenticationSuccessHandler oidcAuthenticationSuccessHandler() {
            return Mockito.mock(OIDCAuthenticationSuccessHandler.class);
        }

        @Bean
        OIDCAuthenticationFailureHandler oidcAuthenticationFailureHandler() {
            return Mockito.mock(OIDCAuthenticationFailureHandler.class);
        }
    }

    @Test
    void testOidcBeansAreNotInstantiatedWhenDisabled() {
        this.contextRunner.withPropertyValues("artemis.user-management.oidc.enabled=false").run(context -> {
            // If oidc is disabled, there are no beans for oidc authentication flow
            assertThat(context).doesNotHaveBean(OIDCConfiguration.class);
            assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
        });
    }

    @Test
    void testOidcBeansAndFilterChainAreWiredWhenEnabled() {
        this.contextRunner
                .withPropertyValues("artemis.user-management.oidc.enabled=true", "spring.security.oauth2.client.registration.oidc.client-id=test-id",
                        "spring.security.oauth2.client.registration.oidc.client-secret=test-secret", "spring.security.oauth2.client.provider.oidc.issuer-uri=http://test-issuer")
                .run(context -> {
                    // if oidc is enabled, there are according beans
                    assertThat(context).hasSingleBean(OIDCConfiguration.class);
                    assertThat(context).hasSingleBean(ClientRegistrationRepository.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                });
    }
}
