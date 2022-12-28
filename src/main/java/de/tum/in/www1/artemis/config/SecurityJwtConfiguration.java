package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.security.SecurityUtils.AUTHORITIES_KEY;
import static de.tum.in.www1.artemis.security.SecurityUtils.JWT_ALGORITHM;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;

import de.tum.in.www1.artemis.management.SecurityMetersService;

@Configuration
public class SecurityJwtConfiguration {

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    // TODO: merge this implementation with JWTConfigurer (defined) in SecurityConfiguration and with JWTFilter
    /**
     * configures the JWT decoder
     * @param metersService the service to track in case wrong tokens have been used
     * @return a JWT decoder using the Nimbus implementation
     */
    // @Bean
    public JwtDecoder jwtDecoder(SecurityMetersService metersService) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(getSecretKey()).macAlgorithm(JWT_ALGORITHM).build();
        return token -> {
            try {
                return jwtDecoder.decode(token);
            }
            catch (Exception ex) {
                if (ex.getMessage().contains("Invalid signature")) {
                    metersService.trackTokenInvalidSignature();
                }
                else if (ex.getMessage().contains("Jwt expired at")) {
                    metersService.trackTokenExpired();
                }
                else if (ex.getMessage().contains("Invalid JWT serialization")) {
                    metersService.trackTokenMalformed();
                }
                else if (ex.getMessage().contains("Invalid unsecured/JWS/JWE")) {
                    metersService.trackTokenMalformed();
                }
                throw ex;
            }
        };
    }

    // @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(getSecretKey()));
    }

    /**
     * configures an authentication converter based on the provided authorities key
     * @return the authentication converter
     */
    // @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("");
        grantedAuthoritiesConverter.setAuthoritiesClaimName(AUTHORITIES_KEY);

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    /**
     * creates a default bearer token resolver
     * @return the resolver
     */
    // @Bean
    public BearerTokenResolver bearerTokenResolver() {
        var bearerTokenResolver = new DefaultBearerTokenResolver();
        bearerTokenResolver.setAllowUriQueryParameter(true);
        return bearerTokenResolver;
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = Base64.from(jwtKey).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
    }
}
