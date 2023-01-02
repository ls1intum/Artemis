package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.util.Optional;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.JWTConfigurer;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.web.filter.SpaWebFilter;
import jakarta.annotation.PostConstruct;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserDetailsService userDetailsService;

    private final TokenProvider tokenProvider;

    private final PasswordService passwordService;

    private final Optional<AuthenticationProvider> remoteUserAuthenticationProvider;

    @Value("${spring.prometheus.monitoringIp:#{null}}")
    private Optional<String> monitoringIpAddress;

    public SecurityConfiguration(AuthenticationManagerBuilder authenticationManagerBuilder, UserDetailsService userDetailsService, TokenProvider tokenProvider,
            PasswordService passwordService, Optional<AuthenticationProvider> remoteUserAuthenticationProvider) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userDetailsService = userDetailsService;
        this.tokenProvider = tokenProvider;
        this.passwordService = passwordService;
        this.remoteUserAuthenticationProvider = remoteUserAuthenticationProvider;
    }

    /**
     * initialize the security configuration by specifying that the (internal) user details service and (if available) an external authentication provider (e.g. JIRA)
     * should be used
     */
    @PostConstruct
    public void init() {
        try {
            // here we configure 2 authentication provider: 1) the user details service for internal authentication using the Artemis database...
            authenticationManagerBuilder.userDetailsService(userDetailsService);
            // ... and 2), if specified a remote (or external) user authentication provider (e.g. JIRA)
            remoteUserAuthenticationProvider.ifPresent(authenticationManagerBuilder::authenticationProvider);
            // When users try to authenticate, Spring will always first ask the remote user authentication provider (e.g. JIRA) if available, and only if this one fails,
            // it will ask the user details service (internal DB) for authentication.
        }
        catch (Exception e) {
            throw new BeanInitializationException("Security configuration failed", e);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return this.passwordService.getPasswordEncoder();
    }

    @Bean
    public DefaultMethodSecurityExpressionHandler methodExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy());
        return expressionHandler;
    }

    @Bean
    RoleHierarchy roleHierarchy() {
        var roleHierarchy = new RoleHierarchyImpl();
        roleHierarchy.setHierarchy("ROLE_ADMIN > ROLE_INSTRUCTOR > ROLE_EDITOR > ROLE_TA > ROLE_USER > ROLE_ANONYMOUS");
        return roleHierarchy;
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives("script-src 'self' 'unsafe-inline' 'unsafe-eval'"))
            .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            .httpStrictTransportSecurity().disable() // this is already configured using nginx
            .permissionsPolicy(permissions -> permissions.policy("camera=(), fullscreen=(*), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()")))
        .authorizeHttpRequests(auth -> auth
            // options: TODO: do we really need this?
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // api
            // TODO: Remove public exceptions once moving public endpoints to /api/public
            .requestMatchers(HttpMethod.POST, "/api/register").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/register").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/activate").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/{v\\d+}/activate").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/authenticate").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/authenticate").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/authenticate").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/{v\\d+}/authenticate").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/account/reset-password/init").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/account/reset-password/init").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/account/reset-password/finish").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/account/reset-password/finish").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/lti/launch/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/lti/launch/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/lti13/auth-callback").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/lti13/auth-callback").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/system-notifications").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/{v\\d+}/api/system-notifications/active").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/programming-exercises/new-result").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/programming-exercises/new-result").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/programming-submissions/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/programming-submissions/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/programming-exercises/test-cases-changed/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/api/programming-exercises/test-cases-changed/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/athene-result/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/{v\\d+}/api/athene-result/*").permitAll()
            .requestMatchers("/api/admin/**").hasAuthority(Role.ADMIN.getAuthority())
            .requestMatchers("/api/{v\\d+}/admin/**").hasAuthority(Role.ADMIN.getAuthority())
            .requestMatchers("/api/**").authenticated()
            // websockets
            .requestMatchers("/websocket/**").permitAll()
            // management
            .requestMatchers("/management/health").permitAll()
            .requestMatchers("/management/info").permitAll()
             // Only allow the configured IP address to access the prometheus endpoint, or allow 127.0.0.1 if none is specified
            .requestMatchers("/management/prometheus/**").access((authentication, context) ->
                    new AuthorizationDecision(context.getRequest().getRemoteAddr().equals(monitoringIpAddress.orElse("127.0.0.1"))))
            .requestMatchers("/management/**").hasAuthority(Role.ADMIN.getAuthority())
            // others
            .requestMatchers("/time").permitAll()
            .requestMatchers("/", "/index.html", "/*.js", "/*.map", "/*.css").permitAll()
            .requestMatchers("/*.ico", "/*.png", "/*.svg", "/*.webapp").permitAll()
            .requestMatchers("/public/**").permitAll()
            .requestMatchers("/logo/*.svg", "/logo/*.png", "/logo/*.ico", "/logo/browserconfig.xml").permitAll()    // for favicons and logos
            .requestMatchers("/ngsw.json").permitAll()    // for the service worker
            .requestMatchers("/app/**").permitAll() //TODO: this might not work for us, because we don't use the app prefix
            .requestMatchers("/i18n/**").permitAll()
            .requestMatchers("/content/**").permitAll()
            .requestMatchers("/api.html").permitAll()
            .requestMatchers("/api-docs/**").permitAll()
            .requestMatchers(CustomLti13Configurer.JWKS_PATH).permitAll())
//        .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)    //TODO: we use JWTFilter, etc. at the moment, but we should switch to this solution
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
            .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
        )
        .apply(securityConfigurerAdapter());

        http.apply(new CustomLti13Configurer());
        // @formatter:on

        return http.build();
    }

    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }
}
