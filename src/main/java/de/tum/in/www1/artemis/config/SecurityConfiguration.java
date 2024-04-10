package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.CorsFilter;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.JWTConfigurer;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.web.filter.SpaWebFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@Import(SecurityProblemSupport.class)
@Profile(PROFILE_CORE)
public class SecurityConfiguration {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserDetailsService userDetailsService;

    private final TokenProvider tokenProvider;

    private final PasswordService passwordService;

    private final Optional<AuthenticationProvider> remoteUserAuthenticationProvider;

    private final CorsFilter corsFilter;

    private final SecurityProblemSupport problemSupport;

    private final ProfileService profileService;

    @Value("#{'${spring.prometheus.monitoringIp:127.0.0.1}'.split(',')}")
    private List<String> monitoringIpAddresses;

    public SecurityConfiguration(AuthenticationManagerBuilder authenticationManagerBuilder, UserDetailsService userDetailsService, TokenProvider tokenProvider,
            PasswordService passwordService, Optional<AuthenticationProvider> remoteUserAuthenticationProvider, CorsFilter corsFilter, SecurityProblemSupport problemSupport,
            ProfileService profileService) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userDetailsService = userDetailsService;
        this.tokenProvider = tokenProvider;
        this.passwordService = passwordService;
        this.remoteUserAuthenticationProvider = remoteUserAuthenticationProvider;
        this.corsFilter = corsFilter;
        this.problemSupport = problemSupport;
        this.profileService = profileService;
    }

    /**
     * Initialize the security configuration by specifying the user details service for internal users and, optionally, an external authentication provider
     * (e.g., {@link de.tum.in.www1.artemis.service.connectors.ldap.LdapAuthenticationProvider}).
     * Spring Security will attempt to authenticate with the providers in the order they're added. If an external provider is configured, it will be queried
     * first; the internal database is used as a fallback if external authentication fails or is not configured.
     */
    @PostConstruct
    public void init() {
        try {
            // Configure the user details service for internal authentication using the Artemis database.
            authenticationManagerBuilder.userDetailsService(userDetailsService);
            // Optionally configure an external authentication provider (e.g., {@link de.tum.in.www1.artemis.service.connectors.ldap.LdapAuthenticationProvider}) for remote user
            // authentication.
            remoteUserAuthenticationProvider.ifPresent(authenticationManagerBuilder::authenticationProvider);
            // Spring Security processes authentication providers in the order they're added. If an external provider is configured,
            // it will be tried first. The internal database-backed provider serves as a fallback if external authentication is not available or fails.
        }
        catch (Exception e) {
            throw new BeanInitializationException("Security configuration failed", e);
        }
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
        var roleHierarchy = new RoleHierarchyImpl();
        roleHierarchy.setHierarchy("ROLE_ADMIN > ROLE_INSTRUCTOR > ROLE_EDITOR > ROLE_TA > ROLE_USER > ROLE_ANONYMOUS");
        return roleHierarchy;
    }

    /**
     * Configures the {@link SecurityFilterChain} for the application's security, specifying how requests should be secured.
     * <p>
     * Through a fluent API, this method configures {@link HttpSecurity} to establish security constraints on HTTP requests.
     * Among the configurations, it disables CSRF protection (as this might be handled client-side or deemed unnecessary),
     * sets up CORS filtering, and customizes exception handling for authentication and access denial. It also defines a
     * content security policy, frame options, and other header settings for enhanced security. Session management is set
     * to stateless to support RESTful and SPA-oriented architectures.
     * </p>
     * <p>
     * Specific access rules for various endpoints are declared, allowing for fine-grained control over who can access
     * what resources, with certain paths being publicly accessible and others requiring specific roles. Additionally,
     * custom security configurations may be added, such as support for LTI if active.
     * </p>
     *
     * @param http The {@link HttpSecurity} to configure.
     * @return The configured {@link SecurityFilterChain}.
     * @throws Exception If an error occurs during the configuration.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(handler -> handler.authenticationEntryPoint(problemSupport).accessDeniedHandler(problemSupport))
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self' 'unsafe-inline' 'unsafe-eval'"))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity((HeadersConfigurer.HstsConfig::disable)) // this is already configured using nginx
                .permissionsPolicy(permissions -> permissions.policy("camera=(), fullscreen=(*), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()")))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/", "/index.html", "/public/**").permitAll()
                .requestMatchers("/*.js", "/*.css", "/*.map", "/*.json").permitAll()
                .requestMatchers("/manifest.webapp", "/robots.txt").permitAll()
                .requestMatchers("/content/**", "/i18n/*.json", "/logo/*").permitAll()
                .requestMatchers("/management/info", "/management/health").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority(Role.ADMIN.getAuthority())
                .requestMatchers("/api/{version:v\\d+}/admin/**").hasAuthority(Role.ADMIN.getAuthority())
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/{version:v\\d+}/public/**").permitAll()
                .requestMatchers("/websocket/**").permitAll()
                .requestMatchers("/.well-known/jwks.json").permitAll()
                .requestMatchers("/git/**").permitAll()
                .requestMatchers("/management/prometheus/**").access((authentication, context) -> new AuthorizationDecision(monitoringIpAddresses.contains(context.getRequest().getRemoteAddr())))
                .requestMatchers("/**").authenticated()
            )
            .with(securityConfigurerAdapter(), configurer -> configurer.configure(http));
        // @formatter:on

        if (profileService.isLtiActive()) {
            http.with(new CustomLti13Configurer(), configurer -> configurer.configure(http));
        }

        return http.build();
    }

    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }
}
