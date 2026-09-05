package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import de.tum.cit.aet.artemis.account.security.ArtemisInternalAuthenticationProvider;
import de.tum.cit.aet.artemis.account.security.passkey.ArtemisPasskeyWebAuthnConfigurer;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.filter.SpaWebFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JWTConfigurer;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.core.service.PasskeyTokenRenewalService;
import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;

/**
 * Configuration class defining authentication and authorization mechanism for all application endpoints
 * We don't make it lazy as it definitely should be instantiated at startup and this happens anyway. So, no negative effect on startup performance.
 */
@Configuration
@EnableWebSecurity
@Lazy(value = false)
@EnableMethodSecurity(securedEnabled = true)
@Profile(PROFILE_CORE)
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    private final CorsFilter corsFilter;

    private final Optional<CustomLti13Configurer> customLti13Configurer;

    private final Optional<ArtemisPasskeyWebAuthnConfigurer> passkeyWebAuthnConfigurer;

    private final JWTCookieService jwtCookieService;

    /**
     * Instantiated at startup even though it is only called while a session is rotated: this class is a
     * {@code @Configuration}, so an eager consumer pulls the service in regardless of the {@code @Lazy} on the service
     * itself. Deferring it would need {@code @Lazy} on this parameter or an {@code ObjectProvider}, and
     * {@code ArchitectureTest.ensureLazyAnnotationNotUsedOnParameters} forbids the first while
     * {@code ensureObjectProviderNotUsedForCircularDependencies} discourages the second, so the one startup bean is
     * accounted for in the bean-instantiation threshold instead.
     */
    private final PasskeyTokenRenewalService passkeyTokenRenewalService;

    /**
     * The longest a "remember me" session may live, measured from the original login. Defaults to the thirty days a single
     * non-rotating token was valid for before rotation existed, so the maximum session length is unchanged.
     * <p>
     * This is the only bound on a session, deliberately. Counting extensions instead would make the maximum depend on when
     * requests happen to arrive: a rotation fires on the first request after less than half the validity remains, so a
     * continuously active session consumes its allowance in half-windows and would end sooner than one that returns just
     * before each expiry - the most active users getting the shortest sessions. Measuring from {@code issuedAt} is
     * independent of request timing, and {@code issuedAt} is as tamper-proof as any other claim in the signed token.
     * <p>
     * It also bounds the renewal lookups on its own: with a validity of {@code V} a session can rotate at most
     * {@code ceiling / (V / 2)} times, about eight over thirty days with the shipped seven-day validity.
     * <p>
     * A ceiling is also the only thing that bounds an externally managed session, because a password reset or a
     * deactivation performed in LDAP, SAML or OIDC leaves no trace in the local account fields the other renewal checks
     * read.
     */
    private final long maxSessionLifetimeInSeconds;

    private final PasswordService passwordService;

    private final TokenProvider tokenProvider;

    private final ModuleFeatureService moduleFeatureService;

    /**
     * Resolved when a request arrives rather than injected: this class builds the security filter chain during startup,
     * and reaching for the service there would pull it into the startup graph for a decision no request needs yet.
     */
    private final ObjectProvider<ElevatedAccessService> elevatedAccessService;

    @Value("${artemis.user-management.passkey.token-validity-in-seconds-for-passkey:15552000}")
    private long tokenValidityInSecondsForPasskey;

    @Value("#{'${spring.prometheus.monitoringIp:127.0.0.1}'.split(',')}")
    private List<String> monitoringIpAddresses;

    /**
     * Validates the configuration of the validity duration of passkey generated jwts
     *
     * @throws IllegalStateException if the server URL configuration is invalid
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validatePasskeyAllowedOriginConfiguration() {
        if (moduleFeatureService.isPasskeyEnabled()) {
            if (tokenValidityInSecondsForPasskey <= 0) {
                throw new IllegalStateException("Token validity in seconds for passkey must be greater than 0 when passkey authentication is enabled.");
            }
        }
    }

    public SecurityConfiguration(CorsFilter corsFilter, Optional<CustomLti13Configurer> customLti13Configurer, Optional<ArtemisPasskeyWebAuthnConfigurer> passkeyWebAuthnConfigurer,
            PasswordService passwordService, TokenProvider tokenProvider, JWTCookieService jwtCookieService, PasskeyTokenRenewalService passkeyTokenRenewalService,
            ModuleFeatureService moduleFeatureService, ObjectProvider<ElevatedAccessService> elevatedAccessService,
            @Value("${artemis.user-management.max-session-lifetime-in-seconds:2592000}") long maxSessionLifetimeInSeconds) {
        this.corsFilter = corsFilter;
        this.customLti13Configurer = customLti13Configurer;
        this.passkeyWebAuthnConfigurer = passkeyWebAuthnConfigurer;
        this.passwordService = passwordService;
        this.tokenProvider = tokenProvider;
        this.jwtCookieService = jwtCookieService;
        this.passkeyTokenRenewalService = passkeyTokenRenewalService;
        this.moduleFeatureService = moduleFeatureService;
        this.elevatedAccessService = elevatedAccessService;
        this.maxSessionLifetimeInSeconds = requireUsableSessionLifetime(maxSessionLifetimeInSeconds);
    }

    /**
     * Rejects a session lifetime that cannot be turned into milliseconds, at startup rather than per request.
     * <p>
     * {@link de.tum.cit.aet.artemis.core.security.jwt.JWTFilter} converts this ceiling with
     * {@code Math.multiplyExact(sessionCeilingInSeconds, 1000)} while rotating a remember-me token, so a value above
     * {@code Long.MAX_VALUE / 1000} would overflow and throw on every renewal, turning a configuration mistake into a
     * request-time failure for the users it affects. A value below one second is rejected for the opposite reason: it
     * expresses a session that is over before it begins, which is a typo rather than an intent.
     *
     * @param lifetimeInSeconds the configured lifetime
     * @return the same value, once it is known to be usable
     */
    private static long requireUsableSessionLifetime(long lifetimeInSeconds) {
        if (lifetimeInSeconds < 1 || lifetimeInSeconds > Long.MAX_VALUE / 1000) {
            throw new IllegalStateException("artemis.user-management.max-session-lifetime-in-seconds must be between 1 and " + Long.MAX_VALUE / 1000
                    + " seconds, so that it can be converted to milliseconds while a session is renewed, but it is " + lifetimeInSeconds);
        }
        return lifetimeInSeconds;
    }

    /**
     * Configures the {@link AuthenticationManager} with the appropriate authentication providers.
     * <p>
     * Spring Security attempts to authenticate with providers in the order they're added:
     * <ol>
     * <li>If an external provider (e.g., LDAP) is configured, it is tried first. The external provider will skip
     * internal users (returns null) and only authenticate external users or users not yet in the database.</li>
     * <li>The internal provider serves as a fallback, authenticating only internal users stored in the Artemis database.</li>
     * </ol>
     * <p>
     * This explicit configuration is required to:
     * <ul>
     * <li>Ensure the correct provider order (external first, internal second)</li>
     * <li>Prevent Spring Boot's auto-configuration from creating a parent AuthenticationManager that would register
     * providers twice (as both parent and child), which could cause authentication issues</li>
     * </ul>
     *
     * @param http                                  The {@link HttpSecurity} to configure.
     * @param artemisInternalAuthenticationProvider The {@link ArtemisInternalAuthenticationProvider} for internal authentication using the Artemis database.
     * @param externalUserAuthenticationProvider    An optional {@link AuthenticationProvider} for external authentication (e.g., LDAP).
     * @return The {@link AuthenticationManager} to use for authenticating users.
     */
    @Bean
    @Primary
    public AuthenticationManager authenticationManager(HttpSecurity http, ArtemisInternalAuthenticationProvider artemisInternalAuthenticationProvider,
            @Qualifier("ldapAuthenticationProvider") Optional<AuthenticationProvider> externalUserAuthenticationProvider) {
        var builder = http.getSharedObject(AuthenticationManagerBuilder.class);

        // External provider (e.g., LDAP) is added first - it will be tried first and skip internal users by returning null
        externalUserAuthenticationProvider.ifPresent(builder::authenticationProvider);

        // Internal provider is added second - it serves as a fallback for internal users
        builder.authenticationProvider(artemisInternalAuthenticationProvider);

        // Explicitly set parent to null to prevent Spring Boot's auto-configured AuthenticationManager from being used as parent.
        // Without this, providers could be registered twice (in parent and child), causing duplicate authentication attempts.
        builder.parentAuthenticationManager(null);

        return builder.build();
    }

    /**
     * Returns 401 Unauthorized for unauthenticated requests.
     *
     * @return the authentication entry point
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Delegates access-denied failures to the HandlerExceptionResolver so that
     * {@link de.tum.cit.aet.artemis.core.exception.ExceptionTranslator} can produce ProblemDetail responses.
     *
     * @param resolver the exception resolver to delegate to
     * @return the access denied handler
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (request, response, accessDeniedException) -> resolver.resolveException(request, response, null, accessDeniedException);
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
    // Renamed for clarity; Spring Security 7 auto-detects this bean by type, not by name
    @Bean
    public DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy());
        return expressionHandler;
    }

    /**
     * Defines the hierarchy of roles within the application's security context.
     * <p>
     * Administrator identity and teaching roles are separate. Administrators remain regular users, but only explicit teaching authorities or passkey-backed administrator elevation
     * can satisfy teaching-role authorization.
     * </p>
     *
     * @return A {@link RoleHierarchy} instance with a predefined hierarchy of roles, ready to be used by the
     *         Spring Security framework to evaluate permissions across the application.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_ADMIN
                ROLE_ADMIN > ROLE_USER
                ROLE_INSTRUCTOR > ROLE_EDITOR > ROLE_TA > ROLE_USER > ROLE_ANONYMOUS
                """);
    }

    /**
     * Content Security Policy directives applied to every HTTP response.
     *
     * <p>
     * Kept as a package-private constant so that unit tests can assert the exact policy without spinning up a full Spring Boot context.
     * </p>
     *
     * <p>
     * NOTE: Additional origins required by the YouTube IFrame API (e.g. {@code s.ytimg.com}) should be verified during manual testing (Task 18) and added here if
     * the browser blocks them.
     * </p>
     */
    static final String CSP_POLICY_DIRECTIVES = "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://www.youtube.com; worker-src 'self' blob:";

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
     * @param http          The {@link HttpSecurity} object to configure security settings for HTTP requests.
     * @param entryPoint    The {@link AuthenticationEntryPoint} to handle unauthenticated requests.
     * @param deniedHandler The {@link AccessDeniedHandler} to handle access denied responses.
     * @return The configured {@link SecurityFilterChain}.
     * @throws Exception If an error occurs during the configuration process.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint entryPoint, AccessDeniedHandler deniedHandler) throws Exception {
        // @formatter:off
        http
            // Disables CSRF (Cross-Site Request Forgery) protection; useful in stateless APIs where the token management is unnecessary.
            .csrf(CsrfConfigurer::disable)
            // Adds a CORS (Cross-Origin Resource Sharing) filter before the username/password authentication to handle cross-origin requests.
            .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
            // Configures exception handling with a custom entry point and access denied handler for authentication issues.
            .exceptionHandling(handler -> handler.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
            // Adds a custom filter for Single Page Applications (SPA), i.e. the client, after the basic authentication filter.
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
            // Configures security headers.
            .headers(headers -> headers
                // Sets Content Security Policy (CSP) directives to prevent XSS attacks.
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(CSP_POLICY_DIRECTIVES)
                )
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
                    .requestMatchers("/content/**", "/i18n/*.json", "/logo/*", "/assets/katex/**").permitAll()
                    // Information and health endpoints do not need authentication. `info` is fetched by the client before
                    // login (profile info, feature flags, version), and the health group is used by probes and the client
                    // status page. These must stay ahead of the `/management/**` admin rule below so they keep matching first.
                    .requestMatchers("/management/info", "/management/health", "/management/health/readiness", "/management/health/liveness").permitAll()
                    // Admin area requires specific authority. Both the canonical `/api/admin/**` prefix and the per-module
                    // `/api/*/admin/**` shape are listed: a single `*` matches exactly one path segment, so `/api/*/admin/**`
                    // alone does not cover `/api/admin/**` (used by the admin module's own controllers).
                    .requestMatchers("/api/admin/**", "/api/*/admin/**").hasAuthority(Role.ADMIN.getAuthority())
                    // Publicly accessible API endpoints (allowed for everyone, potentially with secret authentication).
                    .requestMatchers("/api/*/public/**").permitAll()
                    .requestMatchers("/api/*/internal/**").permitAll()
                    // Websocket and other specific endpoints allowed without authentication.
                    .requestMatchers("/websocket/**").permitAll()
                    .requestMatchers("/.well-known/jwks.json").permitAll()
                    .requestMatchers("/.well-known/assetlinks.json").permitAll()
                    .requestMatchers("/.well-known/apple-app-site-association").permitAll()
                    // Prometheus endpoint protected by IP address.
                    .requestMatchers("/management/prometheus/**").access((_, context) -> new AuthorizationDecision(monitoringIpAddresses.contains(context.getRequest().getRemoteAddr())))
                    // The remaining /management/** paths are administrative. The public exceptions (info, health) and
                    // the IP-gated prometheus rule are matched earlier, so this rule covers the rest. Actuator
                    // endpoints are not served by an annotated handler, so this is the only place that can ask for
                    // administrator elevation on their behalf. It asks the same service every other administrator
                    // decision asks, which weighs the persisted account as well as the session: a token cannot be
                    // revoked, so an account that was deactivated, deleted or demoted has to be rejected here rather
                    // than trusted until the token expires.
                    .requestMatchers("/management/**").access((authentication, _) ->
                        new AuthorizationDecision(elevatedAccessService.getObject().isAdminElevationActive(authentication.get())))
                    .requestMatchers(("/api-docs")).permitAll()
                    .requestMatchers(("/api-docs.yaml")).permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/api/core/calendar/courses/*/calendar-events-ics").permitAll() // Deprecated, to be removed Oct 2026
                    .requestMatchers("/api/calendar/courses/*/calendar-events-ics").permitAll()
                    // `/git/**` endpoints (JGit servlet + LocalVC filters) are only registered under the `localvc` profile
                    // LocalVCFetchFilter/LocalVCPushFilter handle auth
                    .requestMatchers("/git/**").permitAll();

                if (moduleFeatureService.isPasskeyEnabled()) {
                    log.info("Passkey authentication is enabled; permitting /login/webauthn endpoint for all users.");
                    requests.requestMatchers("/login/webauthn").permitAll();
                }

                // only enable sharing endpoints if the sharing module feature is enabled
                if (moduleFeatureService.isSharingEnabled()) {
                    log.info("Sharing module feature is enabled; enabling sharing endpoints (permitAll with security token).");
                    requests
                        // sharing export (to sharing platform) is protected by explicit security tokens, thus we can permitAll here
                        .requestMatchers("/api/programming/sharing/export/**").permitAll()
                        // sharing is protected by explicit security tokens, (or are non-critical) thus we can permitAll here
                        .requestMatchers("/api/core/sharing/**").permitAll();
                }

                // All other requests must be authenticated. Additional authorization happens on the endpoints themselves.
                requests.requestMatchers("/**").authenticated();
            })
            // Applies additional configurations defined in a custom security configurer adapter.
            .with(securityConfigurerAdapter(), configurer -> configurer.configure(http));

        // @formatter:on

        // Configure WebAuthn passkey if enabled
        if (moduleFeatureService.isPasskeyEnabled()) {
            log.info("Passkey authentication is enabled; configuring WebAuthn support.");
            passkeyWebAuthnConfigurer.orElseThrow(() -> new IllegalStateException("Passkey enabled but SecurityConfigurer could not be injected")).configure(http);
        }

        // Conditionally adds configuration for LTI if it is enabled.
        if (moduleFeatureService.isLtiEnabled()) {
            // Activates the LTI endpoints and filters.
            log.info("LTI module feature is enabled; enabling LTI endpoints and security configuration.");
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
        return new JWTConfigurer(tokenProvider, jwtCookieService, tokenValidityInSecondsForPasskey, passkeyTokenRenewalService, maxSessionLifetimeInSeconds);
    }

}
