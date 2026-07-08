package de.tum.cit.aet.artemis.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;

import de.tum.cit.aet.artemis.account.security.OIDCAuthenticationSuccessHandler;
import de.tum.cit.aet.artemis.account.security.OIDCService;

/**
 * Describes the security configuration for OpenID Connect (OIDC) authentication.
 * Since this configuration is annotated with {@link Order} and has a higher precedence
 * than the default {@link de.tum.cit.aet.artemis.core.config.SecurityConfiguration},
 * it intercepts OIDC-specific endpoints first.
 */
@Configuration
@Lazy
@Conditional(OIDCEnabled.class)
public class OIDCConfiguration {

    private final OIDCService oidcService;

    private final OIDCAuthenticationSuccessHandler oidcAuthenticationSuccessHandler;

    private final Environment environment;

    @Value("${spring.security.oauth2.client.registration.oidc.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.oidc.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.oidc.authorization-uri}")
    private String authorizationUri;

    @Value("${spring.security.oauth2.client.provider.oidc.token-uri}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.provider.oidc.user-info-uri}")
    private String userInfoUri;

    @Value("${spring.security.oauth2.client.provider.oidc.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${artemis.user-management.oidc.mappings.username}")
    private String usernameClaimKey;

    public OIDCConfiguration(OIDCService oidcService, OIDCAuthenticationSuccessHandler oidcAuthenticationSuccessHandler, Environment environment) {
        this.oidcService = oidcService;
        this.oidcAuthenticationSuccessHandler = oidcAuthenticationSuccessHandler;
        this.environment = environment;
    }

    /**
     * Explicitly defines the ClientRegistrationRepository bean.
     * This guarantees that Spring Security is deterministically aware of 'oidc' provider,
     */
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        // Fetch scopes from environment
        String[] configuredScopes = environment.getProperty("spring.security.oauth2.client.registration.oidc.scope", String[].class);
        if (configuredScopes == null || configuredScopes.length == 0) {
            configuredScopes = new String[] { "openid", "profile", "email" };
        }
        ClientRegistration oidcRegistration = ClientRegistration.withRegistrationId("oidc").clientId(clientId).clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope(configuredScopes)
                .authorizationUri(authorizationUri).tokenUri(tokenUri).userInfoUri(userInfoUri).jwkSetUri(jwkSetUri).userNameAttributeName(usernameClaimKey).clientName("TUM Login")
                .build();

        return new InMemoryClientRegistrationRepository(oidcRegistration);
    }

    /**
     * Creates a separate security filter chain dedicated to handling OAuth2/OIDC login flows.
     * This chain intercepts requests for authorization redirection and the token exchange callback.
     *
     * @param http The Spring HttpSecurity configurer.
     * @return The configured HttpSecurity filter chain.
     * @throws Exception if Spring detects an issue with the security configuration.
     */
    @Bean
    @Order(1)
    protected SecurityFilterChain oidcFilterChain(final HttpSecurity http) throws Exception {
        // @formatter:off
        http
            // /oauth2/authorization/tum-login - user starts the authentication
            // /login/oauth2/code/tum-login - when TUM sends the code
            .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**")

            // Session for redirects
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )

            // Activate Spring Security OAuth2 Login
            .oauth2Login(oauth2 -> oauth2
                .clientRegistrationRepository(clientRegistrationRepository())
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcService))
                .successHandler(this.oidcAuthenticationSuccessHandler)
            );
        // @formatter:on

        return http.build();
    }
}
