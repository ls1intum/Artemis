package de.tum.in.www1.artemis.security.oidc;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;

/**
 * A custom SecurityConfigurer that integrates JWT authentication into Spring Security's filter chain.
 * This configurer is attached to HttpSecurity to apply JWT token verification before processing authentication.
 */
public class OIDCConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    /**
     * Constructs a JWTConfigurer with a specified token provider.
     *
     */
    public OIDCConfigurer() {

    }

    /**
     * Configures the HttpSecurity to add the JWTFilter before the UsernamePasswordAuthenticationFilter.
     * This setup ensures that the JWT tokens are extracted and validated from incoming requests
     * before any username and password authentication is performed.
     *
     * @param http the HttpSecurity to configure.
     */
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
        http.oauth2Login(Customizer.withDefaults()).logout(logout -> logout.logoutSuccessUrl("/"));
    }
}
