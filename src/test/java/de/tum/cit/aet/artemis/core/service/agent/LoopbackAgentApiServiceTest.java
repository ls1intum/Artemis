package de.tum.cit.aet.artemis.core.service.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class LoopbackAgentApiServiceTest {

    @ParameterizedTest
    @ValueSource(strings = { "", "/", "/artemis" })
    void loopbackPreservesTheServedContextPathForJsonAndPlainText(String contextPath) throws Exception {
        String markdown = "# Grüße: λ 中文 — mathematical explanations";
        String apiPath = "/api/core/context-test";
        String expectedPath = (contextPath.equals("/") ? "" : contextPath) + apiPath;
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(expectedPath, exchange -> {
            boolean valid = "Bearer art_pat_context_test".equals(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    && expectedPath.equals(exchange.getRequestURI().getPath());
            if (exchange.getRequestMethod().equals("PATCH")) {
                valid &= MediaType.parseMediaType(exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isCompatibleWith(MediaType.TEXT_PLAIN)
                        && markdown.equals(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 400, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            var servedRequest = new MockHttpServletRequest();
            servedRequest.setContextPath(contextPath);
            servedRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer art_pat_context_test");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servedRequest));
            var api = new LoopbackAgentApiService(JsonObjectMapper.get(), server.getAddress().getPort());

            assertThat(api.send(HttpMethod.GET, apiPath, null).status()).isEqualTo(200);
            assertThat(api.sendText(HttpMethod.PATCH, apiPath, markdown).status()).isEqualTo(200);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
            server.stop(0);
        }
    }
}
