package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.WebAuthnConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.DomainUserDetailsService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.filter.SpaWebFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JWTConfigurer;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.passkey.ArtemisWebAuthnConfigurer;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@Profile(PROFILE_CORE)
public class SecurityConfiguration {

    private final CorsFilter corsFilter;

    private final HttpMessageConverter<Object> converter;

    private final Optional<CustomLti13Configurer> customLti13Configurer;

    private final JWTCookieService jwtCookieService;

    private final PasswordService passwordService;

    private final ProfileService profileService;

    private final PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository;

    private final TokenProvider tokenProvider;

    private final UserCredentialRepository userCredentialRepository;

    private final UserRepository userRepository;

    private final PublicKeyCredentialCreationOptionsRepository publicKeyCredentialCreationOptionsRepository;

    private final PublicKeyCredentialRequestOptionsRepository publicKeyCredentialRequestOptionsRepository;

    @Value("#{'${spring.prometheus.monitoringIp:127.0.0.1}'.split(',')}")
    private List<String> monitoringIpAddresses;

    @Value("${" + Constants.PASSKEY_ENABLED_PROPERTY_NAME + ":false}")
    private boolean passkeyEnabled;

    /**
     * We expect the server URL to equal the client URL
     */
    @Value("${server.url}")
    private String serverUrl;

    @Value("${client.port:${server.port}}")
    private String port;

    private URL clientUrlToRegisterPasskey;

    private URL clientUrlToAuthenticateWithPasskey;

    @PostConstruct
    public void validatePasskeyAllowedOriginConfiguration() {
        if (passkeyEnabled) {
            try {
                clientUrlToRegisterPasskey = new URI(serverUrl).toURL();
                clientUrlToAuthenticateWithPasskey = new URI(serverUrl + ":" + port).toURL();
                if (!port.matches("\\d+")) {
                    throw new IllegalStateException("Invalid port configuration, the port expected to consist of digits only (8080, 80, ...) but was: " + port);
                }
            }
            catch (URISyntaxException | MalformedURLException e) {
                throw new IllegalStateException("Invalid server URL configuration for WebAuthn: " + e.getMessage(), e);
            }
        }
    }

    public SecurityConfiguration(CorsFilter corsFilter, MappingJackson2HttpMessageConverter converter, Optional<CustomLti13Configurer> customLti13Configurer,
            JWTCookieService jwtCookieService, PasswordService passwordService, ProfileService profileService,
            PublicKeyCredentialCreationOptionsRepository publicKeyCredentialCreationOptionsRepository,
            PublicKeyCredentialRequestOptionsRepository publicKeyCredentialRequestOptionsRepository,
            PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository, TokenProvider tokenProvider, UserCredentialRepository userCredentialRepository,
            UserRepository userRepository) {
        this.converter = converter;
        this.corsFilter = corsFilter;
        this.customLti13Configurer = customLti13Configurer;
        this.jwtCookieService = jwtCookieService;
        this.passwordService = passwordService;
        this.profileService = profileService;
        this.publicKeyCredentialCreationOptionsRepository = publicKeyCredentialCreationOptionsRepository;
        this.publicKeyCredentialRequestOptionsRepository = publicKeyCredentialRequestOptionsRepository;
        this.publicKeyCredentialUserEntityRepository = publicKeyCredentialUserEntityRepository;
        this.tokenProvider = tokenProvider;
        this.userCredentialRepository = userCredentialRepository;
        this.userRepository = userRepository;
    }

    /**
     * Spring Security will attempt to authenticate with the providers in the order they're added. If an external provider is configured, it will be queried first;
     * the internal database is used as a fallback if external authentication fails or is not configured.
     *
     * @param http                             The {@link HttpSecurity} to configure.
     * @param userDetailsService               The {@link UserDetailsService} to use for internal authentication. See {@link DomainUserDetailsService} for the current
     *                                             implementation.
     * @param remoteUserAuthenticationProvider An optional {@link AuthenticationProvider} for external authentication (e.g., LDAP).
     * @return The {@link AuthenticationManager} to use for authenticating users.
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, UserDetailsService userDetailsService, Optional<AuthenticationProvider> remoteUserAuthenticationProvider)
            throws Exception {
        var builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        // Configure the user details service for internal authentication using the Artemis database.
        builder.userDetailsService(userDetailsService);
        // Optionally configure an external authentication provider (e.g., {@link de.tum.cit.aet.artemis.service.connectors.ldap.LdapAuthenticationProvider}) for remote user
        // authentication.
        remoteUserAuthenticationProvider.ifPresent(builder::authenticationProvider);
        // Spring Security processes authentication providers in the order they're added. If an external provider is configured,
        // it will be tried first. The internal database-backed provider serves as a fallback if external authentication is not available or fails.
        return builder.build();
    }

    // NOTE: this replaces the old @Import annotation above the class because it does not work with Spring Boot 3.3 and Spring Security 6.3 any more
    @Bean
    public SecurityProblemSupport securityProblemSupport(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new SecurityProblemSupport(resolver);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return this.passwordService.getPasswordEncoder();
    }

    /**
     * Creates and configures a {@link DefaultMethodSecurityExpressionHandler} bean for handling security expressions.
     * <p>
     * This method sets up a {@link DefaultMethodSecurityExpressionHandler} with a role hierarchy,
     * enhancing Spring Security's method security expression handling capabilities. By setting a role hierarchy,
     * it allows the application to interpret security expressions in a way that respects the hierarchy of roles,
     * making authorization decisions more flexible and intuitive.
     * </p>
     *
     * @return A fully configured {@link DefaultMethodSecurityExpressionHandler} instance ready for use
     *         in securing methods based on security expressions.
     */
    @Bean
    public DefaultMethodSecurityExpressionHandler methodExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy());
        return expressionHandler;
    }

    /**
     * Defines the hierarchy of roles within the application's security context.
     * <p>
     * This method configures and returns a {@link RoleHierarchy} bean that establishes a clear hierarchy among
     * different user roles. By setting this hierarchy, the application can enforce security rules in a nuanced manner,
     * acknowledging that some roles inherently include the permissions of others beneath them.
     * The hierarchy defined here starts with the most privileged role, 'ROLE_ADMIN', and cascades down to the least,
     * 'ROLE_ANONYMOUS', ensuring a structured and scalable approach to role-based access control.
     * </p>
     *
     * @return A {@link RoleHierarchy} instance with a predefined hierarchy of roles, ready to be used by the
     *         Spring Security framework to evaluate permissions across the application.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_INSTRUCTOR > ROLE_EDITOR > ROLE_TA > ROLE_USER > ROLE_ANONYMOUS");
    }

    /**
     * Configures the {@link SecurityFilterChain} for the application, specifying security settings for HTTP requests.
     * <p>
     * This method uses a fluent API to configure {@link HttpSecurity} by:
     * <ul>
     * <li>Disabling CSRF protection, as it might be handled client-side or deemed unnecessary for stateless APIs.</li>
     * <li>Setting up CORS filtering.</li>
     * <li>Customizing exception handling for authentication and access denial.</li>
     * <li>Defining content security policy, frame options, and other security headers.</li>
     * <li>Configuring session management to be stateless, suitable for RESTful and SPA-oriented architectures.</li>
     * <li>Specifying access rules for various endpoints, allowing fine-grained control over access based on roles.</li>
     * <li>Adding custom security configurations, such as LTI support if enabled.</li>
     * </ul>
     * </p>
     *
     * @param http                   The {@link HttpSecurity} object to configure security settings for HTTP requests.
     * @param securityProblemSupport The {@link SecurityProblemSupport} instance to handle authentication entry points and access denied responses.
     * @return The configured {@link SecurityFilterChain}.
     * @throws Exception If an error occurs during the configuration process.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemSupport securityProblemSupport) throws Exception {
        // @formatter:off
        http
            // Disables CSRF (Cross-Site Request Forgery) protection; useful in stateless APIs where the token management is unnecessary.
            .csrf(CsrfConfigurer::disable)
            // Adds a CORS (Cross-Origin Resource Sharing) filter before the username/password authentication to handle cross-origin requests.
            .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
            // Configures exception handling with a custom entry point and access denied handler for authentication issues.
            .exceptionHandling(handler -> handler.authenticationEntryPoint(securityProblemSupport).accessDeniedHandler(securityProblemSupport))
            // Adds a custom filter for Single Page Applications (SPA), i.e. the client, after the basic authentication filter.
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
            // Configures security headers.
            .headers(headers -> headers
                // Sets Content Security Policy (CSP) directives to prevent XSS attacks.
                .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self' 'unsafe-inline' 'unsafe-eval'"))
                // Prevents the website from being framed, avoiding clickjacking attacks.
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                // Sets Referrer Policy to limit the amount of referrer information sent with requests.
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Disables HTTP Strict Transport Security as it is managed at the reverse proxy level (typically nginx).
                .httpStrictTransportSecurity((HeadersConfigurer.HstsConfig::disable))
                // Defines Permissions Policy to restrict what features the browser is allowed to use.
                .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), fullscreen=(*), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()")))
            // Configures sessions to be stateless; appropriate for REST APIs where no session is required.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Configures authorization for various URL patterns. The patterns are considered in order.
            .authorizeHttpRequests(requests -> {
                requests
                    // NOTE: Always have a look at {@link de.tum.cit.aet.artemis.core.security.filter.SpaWebFilter} to see which URLs are forwarded to the SPA
                    // Client related URLs and publicly accessible information (allowed for everyone).
                    .requestMatchers("/", "/index.html", "/public/**").permitAll()
                    .requestMatchers("/*.js", "/*.css", "/*.map", "/*.json").permitAll()
                    .requestMatchers("/manifest.webapp", "/robots.txt").permitAll()
                    .requestMatchers("/content/**", "/i18n/*.json", "/logo/*").permitAll()
                    // Information and health endpoints do not need authentication
                    .requestMatchers("/management/info", "/management/health").permitAll()
                    // Admin area requires specific authority.
                    .requestMatchers("/api/*/admin/**").hasAuthority(Role.ADMIN.getAuthority())
                    // Publicly accessible API endpoints (allowed for everyone).
                    .requestMatchers("/api/*/public/**").permitAll()
                    .requestMatchers("/login/webauthn").permitAll()
                    // Websocket and other specific endpoints allowed without authentication.
                    .requestMatchers("/websocket/**").permitAll()
                    .requestMatchers("/.well-known/jwks.json").permitAll()
                    .requestMatchers("/.well-known/assetlinks.json").permitAll()
                    // Prometheus endpoint protected by IP address.
                    .requestMatchers("/management/prometheus/**").access((authentication, context) -> new AuthorizationDecision(monitoringIpAddresses.contains(context.getRequest().getRemoteAddr())));

                    // LocalVC related URLs: LocalVCPushFilter and LocalVCFetchFilter handle authentication on their own
                    if (profileService.isLocalVcsActive()) {
                        requests.requestMatchers("/git/**").permitAll();
                    }

                    // All other requests must be authenticated. Additional authorization happens on the endpoints themselves.
                    requests.requestMatchers("/**").authenticated();
                }
            )
            // Applies additional configurations defined in a custom security configurer adapter.
            .with(securityConfigurerAdapter(), configurer -> configurer.configure(http));
            // FIXME: Enable HTTP Basic authentication so that people can authenticate using username and password against the server's REST API
            //  PROBLEM: This currently would break LocalVC cloning via http based on the LocalVCServletService
            //.httpBasic(Customizer.withDefaults());

        if (passkeyEnabled) {
            WebAuthnConfigurer<HttpSecurity> webAuthnConfigurer = new ArtemisWebAuthnConfigurer<>(
                converter,
                jwtCookieService,
                userRepository,
                publicKeyCredentialUserEntityRepository,
                userCredentialRepository,
                publicKeyCredentialCreationOptionsRepository,
                publicKeyCredentialRequestOptionsRepository
            );
            http.with(webAuthnConfigurer, configurer -> {
                configurer
                    .allowedOrigins(clientUrlToRegisterPasskey.toString(), clientUrlToAuthenticateWithPasskey.toString())
                    .rpId(clientUrlToRegisterPasskey.getHost())
                    .rpName("Artemis");
            });
        }
        // @formatter:on

        // Conditionally adds configuration for LTI if it is active.
        if (profileService.isLtiActive()) {
            // Activates the LTI endpoints and filters.
            http.with(customLti13Configurer.orElseThrow(), configurer -> configurer.configure(http));
        }

        // Builds and returns the SecurityFilterChain.
        return http.build();
    }

    /**
     * Creates and returns a JWTConfigurer instance. This configurer is responsible for integrating JWT-based authentication
     * into the Spring Security filter chain. It configures how the security framework handles JWTs for authorizing requests.
     *
     * @return JWTConfigurer configured with a token provider that generates and validates JWT tokens.
     */
    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }
}
