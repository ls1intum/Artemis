package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.core.config.llm.LlmConfiguration;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * An AI worker context with the real LLM catalog, client factory and Java Gradle adapter, and one preset {@code testpreset} on the endpoint {@code test}.
 */
final class WorkerLlmTestContext {

    private WorkerLlmTestContext() {
    }

    static final String SUCCESS = """
            {"id":"resp-test","model":"test-model","status":"completed","output":[{"type":"message","role":"assistant",
             "content":[{"type":"output_text","text":"OK"}]}],"usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}
            """;

    static HttpServer startProvider(HttpHandler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", handler);
        server.start();
        return server;
    }

    static String baseUrlProperty(HttpServer server) {
        return "artemis.llm.endpoints.test.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    static void reply(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("aiworker"))
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(LlmConfiguration.class, LlmClientService.class, JavaGradleGenerationAdapterService.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new).withPropertyValues("artemis.aiworker.workload=hyperion-generation",
                        "artemis.llm.endpoints.test.base-url=https://example.invalid/v1", "artemis.llm.endpoints.test.api-key=test-key",
                        "artemis.llm.presets.testpreset.endpoint=test", "artemis.llm.presets.testpreset.model=test-model", "artemis.llm.presets.testpreset.api=responses");
    }
}
