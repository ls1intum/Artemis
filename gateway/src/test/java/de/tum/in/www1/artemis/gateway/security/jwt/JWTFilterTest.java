package de.tum.in.www1.artemis.gateway.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;

import de.tum.in.www1.artemis.security.Role;
import reactor.core.publisher.Mono;
import tech.jhipster.config.JHipsterProperties;

class JWTFilterTest {

    private static final long ONE_MINUTE_MS = 60000;

    private TestTokenGenerator testTokenGenerator;

    private JWTFilter jwtFilter;

    @BeforeEach
    public void setup() {
        JHipsterProperties jHipsterProperties = new JHipsterProperties();
        String base64Secret = "fd54a45s65fds737b9aafcb3412e07ed99b267f33413274720ddbb7f6c5e64e9f14075f2d7ed041592f0b7657baf8";
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setBase64Secret(base64Secret);
        jHipsterProperties.getSecurity().getAuthentication().getJwt().setTokenValidityInSeconds(ONE_MINUTE_MS);
        TokenProvider tokenProvider = new TokenProvider(jHipsterProperties);
        testTokenGenerator = new TestTokenGenerator(jHipsterProperties);
        jwtFilter = new JWTFilter(tokenProvider);
    }

    @Test
    void testJWTFilter() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        String jwt = testTokenGenerator.createToken(authentication, false);
        MockServerHttpRequest.BaseBuilder request = MockServerHttpRequest.get("/api/test").header(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        jwtFilter.filter(exchange, it -> Mono.subscriberContext().flatMap(c -> ReactiveSecurityContextHolder.getContext()).map(SecurityContext::getAuthentication)
                .doOnSuccess(auth -> assertThat(auth.getName()).isEqualTo("test-user")).doOnSuccess(auth -> assertThat(auth.getCredentials().toString()).hasToString(jwt)).then())
                .block();
    }

    @Test
    void testJWTFilterInvalidToken() {
        String jwt = "wrong_jwt";
        MockServerHttpRequest.BaseBuilder request = MockServerHttpRequest.get("/api/test").header(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        jwtFilter.filter(exchange, it -> Mono.subscriberContext().flatMap(c -> ReactiveSecurityContextHolder.getContext()).map(SecurityContext::getAuthentication)
                .doOnSuccess(auth -> assertThat(auth).isNull()).then()).block();
    }

    @Test
    void testJWTFilterMissingAuthorization() {
        MockServerHttpRequest.BaseBuilder request = MockServerHttpRequest.get("/api/test");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        jwtFilter.filter(exchange, it -> Mono.subscriberContext().flatMap(c -> ReactiveSecurityContextHolder.getContext()).map(SecurityContext::getAuthentication)
                .doOnSuccess(auth -> assertThat(auth).isNull()).then()).block();
    }

    @Test
    void testJWTFilterMissingToken() {
        MockServerHttpRequest.BaseBuilder request = MockServerHttpRequest.get("/api/test").header(JWTFilter.AUTHORIZATION_HEADER, "Bearer ");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        jwtFilter.filter(exchange, it -> Mono.subscriberContext().flatMap(c -> ReactiveSecurityContextHolder.getContext()).map(SecurityContext::getAuthentication)
                .doOnSuccess(auth -> assertThat(auth).isNull()).then()).block();
    }

    @Test
    void testJWTFilterWrongScheme() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("test-user", "test-password",
                Collections.singletonList(new SimpleGrantedAuthority(Role.STUDENT.getAuthority())));
        String jwt = testTokenGenerator.createToken(authentication, false);
        MockServerHttpRequest.BaseBuilder request = MockServerHttpRequest.get("/api/test").header(JWTFilter.AUTHORIZATION_HEADER, "Basic " + jwt);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        jwtFilter.filter(exchange, it -> Mono.subscriberContext().flatMap(c -> ReactiveSecurityContextHolder.getContext()).map(SecurityContext::getAuthentication)
                .doOnSuccess(auth -> assertThat(auth).isNull()).then()).block();
    }
}
