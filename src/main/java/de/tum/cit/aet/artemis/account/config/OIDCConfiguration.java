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
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.account.security.OIDCAuthenticationFailureHandler;
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

    private final OIDCAuthenticationFailureHandler oidcAuthenticationFailureHandler;

    private final Environment environment;

    @Value("${spring.security.oauth2.client.registration.oidc.client-id:mock-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.oidc.client-secret:mock-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri:http://mock-issuer}")
    private String issuerUri;

    @Value("${spring.security.oauth2.client.provider.oidc.authorization-uri:http://mock-auth}")
    private String authorizationUri;

    @Value("${spring.security.oauth2.client.provider.oidc.token-uri:http://mock-token}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.provider.oidc.user-info-uri:http://mock-user}")
    private String userInfoUri;

    @Value("${spring.security.oauth2.client.provider.oidc.jwk-set-uri:http://mock-jwk}")
    private String jwkSetUri;

    @Value("${artemis.user-management.oidc.mappings.username:preferred_username}")
    private String usernameClaimKey;

    public OIDCConfiguration(OIDCService oidcService, OIDCAuthenticationSuccessHandler oidcAuthenticationSuccessHandler,
            OIDCAuthenticationFailureHandler oidcAuthenticationFailureHandler, Environment environment) {
        this.oidcService = oidcService;
        this.oidcAuthenticationSuccessHandler = oidcAuthenticationSuccessHandler;
        this.oidcAuthenticationFailureHandler = oidcAuthenticationFailureHandler;
        this.environment = environment;
    }

    /**
     * Explicitly defines the ClientRegistrationRepository bean.
     * This guarantees that Spring Security is deterministically aware of 'oidc' provider,
     *
     * @return the configured InMemoryClientRegistrationRepository containing the OIDC registration details.
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
                .issuerUri(issuerUri).authorizationUri(authorizationUri).tokenUri(tokenUri).userInfoUri(userInfoUri).jwkSetUri(jwkSetUri).userNameAttributeName(usernameClaimKey)
                .clientName("TUM Login").build();

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
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository(), "/oauth2/authorization");

        // Extract rememberMe & redirect field parameters query and store it into session
        resolver.setAuthorizationRequestCustomizer(builder -> {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var req = attrs.getRequest();
                var session = req.getSession(true);
                session.setAttribute(OIDCConstants.OIDC_REMEMBER_ME_SESSION_KEY, "true".equalsIgnoreCase(req.getParameter("rememberMe")));

                String redirectTarget = req.getParameter("redirect");
                if (redirectTarget != null && !redirectTarget.isBlank()) {
                    session.setAttribute(OIDCConstants.OIDC_REDIRECT_TARGET_SESSION_KEY, redirectTarget);
                }
                else {
                    session.removeAttribute(OIDCConstants.OIDC_REDIRECT_TARGET_SESSION_KEY);
                }

                String codeChallenge = req.getParameter("code_challenge");
                if (codeChallenge == null || codeChallenge.isBlank()) {
                    codeChallenge = req.getParameter("codeChallenge");
                }
                if (codeChallenge != null && !codeChallenge.isBlank()) {
                    session.setAttribute(OIDCConstants.OIDC_CODE_CHALLENGE_SESSION_KEY, codeChallenge);
                }
                else {
                    session.removeAttribute(OIDCConstants.OIDC_CODE_CHALLENGE_SESSION_KEY);
                }
            }
        });
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
                .authorizationEndpoint(auth -> auth.authorizationRequestResolver(resolver))
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcService))
                .successHandler(this.oidcAuthenticationSuccessHandler)
                .failureHandler(this.oidcAuthenticationFailureHandler)
            );
        // @formatter:on

        return http.build();
    }
}
