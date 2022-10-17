package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.util.Optional;

import javax.annotation.PostConstruct;

import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.CorsFilter;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.JWTConfigurer;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Import(SecurityProblemSupport.class)
public class SecurityConfiguration {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserDetailsService userDetailsService;

    private final TokenProvider tokenProvider;

    private final CorsFilter corsFilter;

    private final SecurityProblemSupport problemSupport;

    private final PasswordService passwordService;

    private final Optional<AuthenticationProvider> remoteUserAuthenticationProvider;

    @Value("${spring.prometheus.monitoringIp:#{null}}")
    private Optional<String> monitoringIpAddress;

    public SecurityConfiguration(AuthenticationManagerBuilder authenticationManagerBuilder, UserDetailsService userDetailsService, TokenProvider tokenProvider, CorsFilter corsFilter, SecurityProblemSupport problemSupport,
        PasswordService passwordService, Optional<AuthenticationProvider> remoteUserAuthenticationProvider) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userDetailsService = userDetailsService;
        this.tokenProvider = tokenProvider;
        this.corsFilter = corsFilter;
        this.problemSupport = problemSupport;
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
    RoleHierarchy roleHierarchy() {
        var roleHierarchy = new RoleHierarchyImpl();
        roleHierarchy.setHierarchy("""
                    ROLE_ADMIN > ROLE_INSTRUCTOR
                    ROLE_INSTRUCTOR > ROLE_EDITOR
                    ROLE_EDITOR > ROLE_TA
                    ROLE_TA > ROLE_USER
                    ROLE_USER > ROLE_ANONYMOUS
                """);
        return roleHierarchy;
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // @formatter:off
        http
            .csrf()
            .disable()
            .addFilterBefore(corsFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling().authenticationEntryPoint(problemSupport)
                .accessDeniedHandler(problemSupport)
        .and()
            .headers()
            .contentSecurityPolicy("script-src 'self' 'unsafe-inline' 'unsafe-eval'")
            // TODO: investigate exactly whether the following works in our setup or not
            // .contentSecurityPolicy("default-src 'self'; connect-src: 'self' 'https://sentry.io' 'ws:' 'wss:'; frame-src * data:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src * data:; font-src 'self' data:")
        .and()
            .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
        .and()
            .permissionsPolicy().policy("camera=(), fullscreen=(*), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()")
        .and()
            .frameOptions()
            .deny()
        .and()
            .headers()
            .httpStrictTransportSecurity()
            .disable() // this is already configured using nginx
        .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
            .authorizeRequests()
            .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .and()
            // api
            .authorizeRequests()
            .antMatchers("/api/register").permitAll()
            .antMatchers("/api/activate").permitAll()
            .antMatchers("/api/authenticate").permitAll()
            .antMatchers("/api/account/reset-password/init").permitAll()
            .antMatchers("/api/account/reset-password/finish").permitAll()
            .antMatchers("/api/lti/launch/*").permitAll()
            .antMatchers("/api/files/attachments/lecture/**").permitAll()
            .antMatchers("/api/files/attachments/attachment-unit/**").permitAll()
            .antMatchers("/api/files/file-upload-exercises/**").permitAll()
            .antMatchers("/api/files/markdown/**").permitAll()
            .antMatchers(HttpMethod.GET, SYSTEM_NOTIFICATIONS_RESOURCE_PATH_ACTIVE_API_PATH).permitAll()
            .antMatchers(HttpMethod.POST, NEW_RESULT_RESOURCE_API_PATH).permitAll()
            .antMatchers(HttpMethod.POST, PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + "*").permitAll()
            .antMatchers(HttpMethod.POST, TEST_CASE_CHANGED_API_PATH + "*").permitAll()
            .antMatchers(HttpMethod.POST, ATHENE_RESULT_API_PATH + "*").permitAll()
            .antMatchers("/api/**").authenticated()
        .and()
            // websocket
            .authorizeRequests()
            .antMatchers("/websocket/tracker").hasAuthority(Role.ADMIN.getAuthority())
            .antMatchers("/websocket/**").permitAll()
        .and()
            // management
            .authorizeRequests()
            .antMatchers("/management/health").permitAll()
            .antMatchers("/management/info").permitAll()
            // Only allow the configured IP address to access the prometheus endpoint, or allow 127.0.0.1 if none is specified
            .antMatchers("/management/prometheus/**").hasIpAddress(monitoringIpAddress.orElse("127.0.0.1"))
            .antMatchers("/management/**").hasAuthority(Role.ADMIN.getAuthority())
        .and()
            // others
            .authorizeRequests()
            .antMatchers("/time").permitAll()
            .antMatchers("/app/**/*.{js,html}").permitAll()
            .antMatchers("/i18n/**").permitAll()
            .antMatchers("/content/**").permitAll()
            .antMatchers("/test/**").permitAll()
            .antMatchers("/api.html").permitAll()
            .antMatchers("/api-docs/**").permitAll()
            .antMatchers("/.well-known/jwks.json").permitAll()
        .and()
            .apply(securityConfigurerAdapter());

        http.apply(lti13Configurer(http));

        // @formatter:on

        return http.build();
    }

    private CustomLti13Configurer lti13Configurer(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(CustomLti13Configurer.class);
    }

    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }

    private OAuth2JWKSService oAuth2JWKSService(HttpSecurity http) {
        return http.getSharedObject(ApplicationContext.class).getBean(OAuth2JWKSService.class);
    }
}
