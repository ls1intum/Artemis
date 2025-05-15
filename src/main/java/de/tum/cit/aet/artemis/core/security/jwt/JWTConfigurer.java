package de.tum.cit.aet.artemis.core.security.jwt;

import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * A custom SecurityConfigurer that integrates JWT authentication into Spring Security's filter chain.
 * This configurer is attached to HttpSecurity to apply JWT token verification before processing authentication.
 */
public class JWTConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    private final TokenProvider tokenProvider;

    private final JWTCookieService jwtCookieService;

    private final UserDetailsService userDetailsService;

    private final long tokenValidityInSecondsForPasskey;

    /**
     * Constructs a JWTConfigurer with a specified token provider.
     *
     * @param tokenProvider the provider responsible for generating and validating JWT tokens.
     */
    public JWTConfigurer(TokenProvider tokenProvider, JWTCookieService jwtCookieService, UserDetailsService userDetailService, long tokenValidityInSecondsForPasskey) {
        this.tokenProvider = tokenProvider;
        this.jwtCookieService = jwtCookieService;
        this.userDetailsService = userDetailService;
        this.tokenValidityInSecondsForPasskey = tokenValidityInSecondsForPasskey;
    }

    /**
     * Configures the HttpSecurity to add the JWTFilter before the UsernamePasswordAuthenticationFilter.
     * This setup ensures that the JWT tokens are extracted and validated from incoming requests
     * before any username and password authentication is performed.
     *
     * @param http the HttpSecurity to configure.
     */
    @Override
    public void configure(HttpSecurity http) {
        JWTFilter customFilter = new JWTFilter(tokenProvider, jwtCookieService, userDetailsService, tokenValidityInSecondsForPasskey);
        // Adds the JWTFilter to the security chain before the UsernamePasswordAuthenticationFilter.
        // This ensures that the JWTFilter processes the request first to extract and validate JWTs.
        http.addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
