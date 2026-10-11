package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
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
    @Lazy
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

    @Test
    void testScopesDefaultToOpenidProfileEmailWhenNoneAreConfigured() {
        assertThat(requestedScopes(enabledOidcContext())).containsExactly("openid", "profile", "email");
    }

    @Test
    void testScopesDefaultToOpenidProfileEmailWhenTheConfiguredValueIsBlank() {
        assertThat(requestedScopes(enabledOidcContext().withPropertyValues("spring.security.oauth2.client.registration.oidc.scope="))).containsExactly("openid", "profile",
                "email");
    }

    @Test
    void testScopesAreReadFromAYamlStyleList() {
        // the form of application-oidc.yml and of every list written in a YAML configuration file
        assertThat(requestedScopes(enabledOidcContext().withPropertyValues("spring.security.oauth2.client.registration.oidc.scope[0]=openid",
                "spring.security.oauth2.client.registration.oidc.scope[1]=profile", "spring.security.oauth2.client.registration.oidc.scope[2]=email",
                "spring.security.oauth2.client.registration.oidc.scope[3]=imMatrikelNr"))).containsExactly("openid", "profile", "email", "imMatrikelNr");
    }

    @Test
    void testScopesAreReadFromIndexedEnvironmentVariables() {
        // the form of the test server deployments: one environment variable per scope
        assertThat(requestedScopes(enabledOidcContext().withInitializer(context -> addSystemEnvironment(context,
                Map.of("SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE_0", "openid", "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE_1", "profile",
                        "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE_2", "email", "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE_3", "imMatrikelNr")))))
                .containsExactly("openid", "profile", "email", "imMatrikelNr");
    }

    @Test
    void testScopesAreReadFromACommaSeparatedEnvironmentVariable() {
        assertThat(requestedScopes(enabledOidcContext()
                .withInitializer(context -> addSystemEnvironment(context, Map.of("SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE", "openid,profile,email,imMatrikelNr")))))
                .containsExactly("openid", "profile", "email", "imMatrikelNr");
    }

    private ApplicationContextRunner enabledOidcContext() {
        return this.contextRunner.withPropertyValues("artemis.user-management.oidc.enabled=true", "spring.security.oauth2.client.registration.oidc.client-id=test-id",
                "spring.security.oauth2.client.registration.oidc.client-secret=test-secret", "spring.security.oauth2.client.provider.oidc.issuer-uri=http://test-issuer");
    }

    /**
     * Adds environment variables the way the operating system provides them. Spring Boot only maps {@code _0} to {@code [0]} for a property source with this exact name.
     */
    private static void addSystemEnvironment(ConfigurableApplicationContext context, Map<String, Object> variables) {
        context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
    }

    private static Set<String> requestedScopes(ApplicationContextRunner runner) {
        AtomicReference<Set<String>> scopes = new AtomicReference<>();
        runner.run(context -> scopes.set(context.getBean(ClientRegistrationRepository.class).findByRegistrationId("oidc").getScopes()));
        return scopes.get();
    }
}
