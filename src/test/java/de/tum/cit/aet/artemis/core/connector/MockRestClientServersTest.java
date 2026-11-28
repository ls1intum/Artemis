package de.tum.cit.aet.artemis.core.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Checks that {@link MockRestClientServers} mocks the remote server of a {@link RestClient} bean that is already built, with a request
 * factory and an interceptor like the clients of {@code RestClientConfiguration}.
 */
class MockRestClientServersTest {

    private AnnotationConfigApplicationContext context;

    private MockRestClientServers servers;

    @Lazy
    @Configuration(proxyBeanMethods = false)
    static class ClientConfiguration {

        private static RestClient client(String baseUrl, String secret) {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setReadTimeout(1000);
            return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).requestInterceptor((request, body, execution) -> {
                request.getHeaders().add("Authorization", secret);
                return execution.execute(request, body);
            }).build();
        }

        @Bean
        RestClient firstRestClient() {
            return client("http://first.example", "first-secret");
        }

        @Bean
        RestClient secondRestClient() {
            return client("http://second.example", "second-secret");
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(MockRestClientServers.class);
        context.register(ClientConfiguration.class);
        context.refresh();
        servers = context.getBean(MockRestClientServers.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void shouldKeepTheBaseUrlAndTheInterceptorOfTheBean() {
        var server = servers.serverOf("firstRestClient");
        server.expect(requestTo("http://first.example/api/health")).andExpect(header("Authorization", "first-secret")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(context.getBean("firstRestClient", RestClient.class).get().uri("/api/health").retrieve().body(String.class)).isEqualTo("ok");

        server.verify();
    }

    @Test
    void shouldGiveEveryBeanItsOwnServerAndAllowAReset() {
        var first = servers.serverOf("firstRestClient");
        var second = servers.serverOf("secondRestClient");
        assertThat(first).isNotSameAs(second);

        first.expect(requestTo("http://first.example/a")).andRespond(withSuccess());
        first.reset();
        first.expect(requestTo("http://first.example/b")).andRespond(withSuccess("b", MediaType.TEXT_PLAIN));

        assertThat(context.getBean("firstRestClient", RestClient.class).get().uri("/b").retrieve().body(String.class)).isEqualTo("b");

        first.verify();
        // The other client received nothing and expected nothing.
        second.verify();
    }

    @Test
    void shouldThrowTheExceptionTypesOfAFailedCall() {
        servers.serverOf("firstRestClient").expect(requestTo("http://first.example/missing")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        var client = context.getBean("firstRestClient", RestClient.class);

        assertThatThrownBy(() -> client.get().uri("/missing").retrieve().body(String.class)).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void shouldFailLikeAnUnreachableServerWhenNoExpectationWasSet() {
        // Nothing asked for the server of this client, like a service that calls a remote server that its test does not mock.
        var client = context.getBean("secondRestClient", RestClient.class);

        assertThatThrownBy(() -> client.get().uri("/api/health").retrieve().body(String.class)).isInstanceOf(ResourceAccessException.class)
                .hasRootCauseInstanceOf(ConnectException.class);
    }

    @Test
    void shouldFailTheTestOnAnUnexpectedRequestOnceAnExpectationWasSet() {
        servers.serverOf("firstRestClient").expect(requestTo("http://first.example/expected")).andRespond(withSuccess());
        var client = context.getBean("firstRestClient", RestClient.class);

        assertThatThrownBy(() -> client.get().uri("/other").retrieve().body(String.class)).isInstanceOf(AssertionError.class);
    }

    @Test
    void shouldNameTheKnownBeansWhenAskedForAnUnknownOne() {
        assertThatThrownBy(() -> servers.serverOf("unknownRestClient")).isInstanceOf(IllegalStateException.class).hasMessageContaining("unknownRestClient")
                .hasMessageContaining("firstRestClient");
    }
}
