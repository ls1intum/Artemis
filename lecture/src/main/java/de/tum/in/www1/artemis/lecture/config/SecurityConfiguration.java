package de.tum.in.www1.artemis.lecture.config;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.util.Optional;

import javax.annotation.PostConstruct;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.CorsFilter;
import org.zalando.problem.spring.web.advice.security.SecurityProblemSupport;

import de.tum.in.www1.artemis.security.PBEPasswordEncoder;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.JWTConfigurer;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Import(SecurityProblemSupport.class)
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserDetailsService userDetailsService;

    private final TokenProvider tokenProvider;

    private final CorsFilter corsFilter;

    private final SecurityProblemSupport problemSupport;

    private final Optional<AuthenticationProvider> remoteUserAuthenticationProvider;

    @Value("${spring.prometheus.monitoringIp:#{null}}")
    private Optional<String> monitoringIpAddress;

    @Value("${artemis.encryption-password}")
    private String encryptionPassword;

    public SecurityConfiguration(AuthenticationManagerBuilder authenticationManagerBuilder, UserDetailsService userDetailsService, TokenProvider tokenProvider,
                                 CorsFilter corsFilter, SecurityProblemSupport problemSupport, Optional<AuthenticationProvider> remoteUserAuthenticationProvider) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userDetailsService = userDetailsService;
        this.tokenProvider = tokenProvider;
        this.corsFilter = corsFilter;
        this.problemSupport = problemSupport;
        this.remoteUserAuthenticationProvider = remoteUserAuthenticationProvider;
    }

    /**
     * Initialize the security configuration by specifying that the (internal) user details service and (if available) an external authentication provider (e.g. JIRA)
     * should be used
     */
    @PostConstruct
    public void init() {
        try {
            // Here we configure 2 authentication providers: 1) the user details service for internal authentication using the Artemis database...
            authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
            // ... and 2), if specified a remote (or external) user authentication provider (e.g. JIRA)
            remoteUserAuthenticationProvider.ifPresent(authenticationManagerBuilder::authenticationProvider);
            // When users try to authenticate, Spring will always first ask the remote user authentication provider (e.g. JIRA) if available, and only if this one fails,
            // it will ask the user details service (internal DB) for authentication.
        } catch (Exception exception) {
            throw new BeanInitializationException("Security configuration failed", exception);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PBEPasswordEncoder(encryptor());
    }

    @Bean
    public StandardPBEStringEncryptor encryptor() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        encryptor.setPassword(encryptionPassword);
        return encryptor;
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

    @Override
    public void configure(WebSecurity web) {
        // @formatter:off
        web.ignoring()
            .antMatchers(HttpMethod.OPTIONS, "/**")
            .antMatchers("/app/**/*.{js,html}")
            .antMatchers("/i18n/**")
            .antMatchers("/content/**")
            .antMatchers("/api-docs/**")
            .antMatchers("/api.html")
            .antMatchers("/test/**");
        web.ignoring()
            .antMatchers(HttpMethod.POST, NEW_RESULT_RESOURCE_API_PATH);
        web.ignoring()
            .antMatchers(HttpMethod.POST, PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + "*");
        web.ignoring()
            .antMatchers(HttpMethod.POST, TEST_CASE_CHANGED_API_PATH + "*");
        web.ignoring()
            .antMatchers(HttpMethod.GET, SYSTEM_NOTIFICATIONS_RESOURCE_PATH_ACTIVE_API_PATH);
        web.ignoring()
            .antMatchers(HttpMethod.POST, ATHENE_RESULT_API_PATH + "*");
        // @formatter:on
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
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
            .and()
            .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            .and()
            .featurePolicy("geolocation 'none'; midi 'none'; sync-xhr 'none'; microphone 'none'; camera 'none'; magnetometer 'none'; gyroscope 'none'; fullscreen *; payment 'none'")
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
            .antMatchers("/api/**").authenticated()
            .antMatchers("/websocket/tracker").hasAuthority(Role.ADMIN.getAuthority())
            .antMatchers("/websocket/**").permitAll()
            .antMatchers("/management/health").permitAll()
            .antMatchers("/management/info").permitAll()
            // Only allow the configured IP address to access the prometheus endpoint, or allow 127.0.0.1 if none is specified
            .antMatchers("/management/prometheus/**").hasIpAddress(monitoringIpAddress.orElse("127.0.0.1"))
            .antMatchers("/management/**").hasAuthority(Role.ADMIN.getAuthority())
            .antMatchers("/time").permitAll()
            .and()
            .apply(securityConfigurerAdapter());
        // @formatter:on
    }

    private JWTConfigurer securityConfigurerAdapter() {
        return new JWTConfigurer(tokenProvider);
    }
}
