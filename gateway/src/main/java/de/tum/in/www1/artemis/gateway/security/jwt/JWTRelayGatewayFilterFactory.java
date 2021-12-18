package de.tum.in.www1.artemis.gateway.security.jwt;

import static de.tum.in.www1.artemis.gateway.security.jwt.JWTFilter.AUTHORIZATION_HEADER;

import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JWTRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final TokenProvider tokenProvider;

    public JWTRelayGatewayFilterFactory(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String token = this.extractJWTToken(exchange.getRequest());
            if (StringUtils.hasText(token) && this.tokenProvider.validateTokenForAuthority(token)) {
                ServerHttpRequest request = exchange.getRequest().mutate().header(AUTHORIZATION_HEADER, "Bearer " + token).build();

                return chain.filter(exchange.mutate().request(request).build());
            }
            return chain.filter(exchange);
        };
    }

    /**
     * Extract the JWT token from the request.
     * Some requests (i.e. /time or public resources like logo) do not require authentication,
     * therefore null is returned if the Authorization header is not set.
     * Exception is thrown is the Authorization header is not set correctly.
     */
    private String extractJWTToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (bearerToken == null) {
            return null;
        }
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new IllegalArgumentException("Invalid token in Authorization header");
    }
}
