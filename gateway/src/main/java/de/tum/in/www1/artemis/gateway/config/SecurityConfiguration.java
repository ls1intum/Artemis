package de.tum.in.www1.artemis.gateway.config;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.zalando.problem.spring.webflux.advice.security.SecurityProblemSupport;

import de.tum.in.www1.artemis.gateway.security.jwt.JWTFilter;
import de.tum.in.www1.artemis.gateway.web.filter.SpaWebFilter;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.security.PBEPasswordEncoder;
import de.tum.in.www1.artemis.security.Role;

@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Import(SecurityProblemSupport.class)
public class SecurityConfiguration {

    private final TokenProvider tokenProvider;

    private final SecurityProblemSupport problemSupport;

    @Value("${artemis.encryption-password}")
    private String encryptionPassword;

    public SecurityConfiguration(TokenProvider tokenProvider, SecurityProblemSupport problemSupport) {
        this.tokenProvider = tokenProvider;
        this.problemSupport = problemSupport;
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

    /**
     * Configures accepted http options, add filters for specific paths related to the user authentication and/or Role
     *
     * @param http
     * @return the configured filter
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        // @formatter:off
        http
            .securityMatcher(new NegatedServerWebExchangeMatcher(new OrServerWebExchangeMatcher(
                pathMatchers("/app/**", "/i18n/**", "/content/**", "/swagger-ui/**", "/swagger-resources/**", "/v2/api-docs", "/v3/api-docs", "/test/**"),
                pathMatchers(HttpMethod.OPTIONS, "/**"))))
            .csrf()
            .disable()
                .addFilterAt(new SpaWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(new JWTFilter(tokenProvider), SecurityWebFiltersOrder.HTTP_BASIC)
                .exceptionHandling().accessDeniedHandler(problemSupport).authenticationEntryPoint(problemSupport)
            .and()
                .headers()
                .contentSecurityPolicy("script-src 'self' 'unsafe-inline' 'unsafe-eval'")
            .and()
                .referrerPolicy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            .and()
                .featurePolicy("geolocation 'none'; midi 'none'; sync-xhr 'none'; microphone 'none'; camera 'none'; magnetometer 'none'; gyroscope 'none'; fullscreen 'self'; payment 'none'")
            .and()
                .frameOptions()
                .disable()
            .and()
                .authorizeExchange()
                .pathMatchers("/").permitAll()
                .pathMatchers("/*.*").permitAll()
                .pathMatchers("/api/authenticate").permitAll()
                .pathMatchers("/api/register").permitAll()
                .pathMatchers("/api/activate").permitAll()
                .pathMatchers("/api/account/reset-password/init").permitAll()
                .pathMatchers("/api/account/reset-password/finish").permitAll()
                .pathMatchers("/api/auth-info").permitAll()
                .pathMatchers("/api/admin/**").hasAuthority(Role.ADMIN.getAuthority())
                .pathMatchers("/api/files/attachments/lecture/**").permitAll()
                .pathMatchers("/api/files/attachments/attachment-unit/**").permitAll()
                .pathMatchers("/api/files/file-upload-exercises/**").permitAll()
                .pathMatchers("/api/files/markdown/**").permitAll()
                .pathMatchers("/api/**").authenticated()
                .pathMatchers("/websocket/tracker").hasAuthority(Role.ADMIN.getAuthority())
                .pathMatchers("/websocket/**").permitAll()
                .pathMatchers("/services/artemis/api/authenticate").permitAll()
                .pathMatchers("/services/artemis/public/**").permitAll()
                .pathMatchers("/services/artemis/time").permitAll()
                .pathMatchers("/services/**").authenticated()
                .pathMatchers("/management/health").permitAll()
                .pathMatchers("/management/health/**").permitAll()
                .pathMatchers("/management/info").permitAll()
                .pathMatchers("/management/prometheus").permitAll()
                .pathMatchers("/management/**").hasAuthority(Role.ADMIN.getAuthority())
                .pathMatchers("/public/**").permitAll()
                .pathMatchers("/logo/**").permitAll()
                .pathMatchers("/time").permitAll();
        // @formatter:on
        return http.build();
    }
}
