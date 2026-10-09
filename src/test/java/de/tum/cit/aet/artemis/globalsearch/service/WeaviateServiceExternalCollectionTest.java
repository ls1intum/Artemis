package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import io.weaviate.client6.v1.api.WeaviateClient;

class WeaviateServiceExternalCollectionTest {

    private HttpServer server;

    private final AtomicReference<String> schemaRequestPath = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", this::respondToClientRequest);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void wrapsAnExternalCollectionConnectionFailureAfterTheSdkInitializes() throws Exception {
        int port = server.getAddress().getPort();
        try (WeaviateClient client = WeaviateClient.connectToCustom(config -> config.scheme("http").httpHost("127.0.0.1").httpPort(port).grpcHost("127.0.0.1").grpcPort(port))) {
            var properties = new WeaviateConfigurationProperties(true, "127.0.0.1", port, port, "http", "Artemis_", "none", null, null, null, null);
            var service = new WeaviateService(Optional.of(client), properties, new MockEnvironment());

            assertThatThrownBy(() -> service.externalCollectionExists("Lectures")).isInstanceOf(WeaviateException.class)
                    .hasMessageStartingWith("Failed to check whether external collection 'Lectures' exists:").hasCauseInstanceOf(IOException.class);
            assertThat(schemaRequestPath).hasValue("/v1/schema/Lectures");
        }
    }

    private void respondToClientRequest(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestURI().getPath()) {
            case "/v1/.well-known/live" -> respond(exchange, 200, "");
            case "/v1/meta" -> respond(exchange, 200, "{\"hostname\":\"loopback\",\"version\":\"1.32.0\",\"modules\":{}}");
            case "/v1/schema/Lectures" -> {
                schemaRequestPath.set(exchange.getRequestURI().getPath());
                exchange.close();
            }
            default -> respond(exchange, 404, "{}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }
}
