package de.tum.in.www1.artemis.gateway.web.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class SpaWebFilter implements WebFilter {

    /**
     * In order to enhance security any unmapped paths that are not part of the api or public resources
     * will be forwarded to the client {@code index.html}
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api") && !path.startsWith("/management") && !path.startsWith("/time") && !path.startsWith("/websocket") && !path.startsWith("/services")
                && !path.startsWith("/swagger") && !path.startsWith("/public") && !path.startsWith("/v2/api-docs") && !path.startsWith("/v3/api-docs")
                && path.matches("[^\\\\.]*")) {
            return chain.filter(exchange.mutate().request(exchange.getRequest().mutate().path("/index.html").build()).build());
        }
        return chain.filter(exchange);
    }
}
