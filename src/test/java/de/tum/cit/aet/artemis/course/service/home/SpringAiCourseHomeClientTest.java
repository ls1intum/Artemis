package de.tum.cit.aet.artemis.course.service.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

class SpringAiCourseHomeClientTest {

    private static final LlmProperties.Preset PRESET = LlmTestCatalog.preset("local", "test-model", LlmProperties.Api.RESPONSES);

    private final CourseHomeAiClient.Input input = new CourseHomeAiClient.Input("Encourage learning.", "Published context.");

    @Test
    void fourBusyWorkersRejectFurtherWorkInsteadOfQueuing() throws Exception {
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var model = model();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await();
            return response();
        });
        try (var client = new SpringAiCourseHomeClient(model, PRESET, new SimpleMeterRegistry())) {
            for (int i = 0; i < 4; i++)
                client.generate(input);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var rejected = client.generate(input);
            assertThat(rejected).isCompletedWithValue(Optional.empty());
        }
        finally {
            release.countDown();
        }
    }

    @Test
    void slowModelWorkReturnsFallbackAtTheBoundedDeadline() throws Exception {
        var release = new CountDownLatch(1);
        var model = model();
        when(model.call(any(Prompt.class))).thenAnswer(i -> {
            release.await();
            return response();
        });
        try (var client = new SpringAiCourseHomeClient(model, PRESET, new SimpleMeterRegistry())) {
            assertThat(SpringAiCourseHomeClient.TIMEOUT).isEqualTo(Duration.ofSeconds(3));
            assertThat(client.generate(input)).succeedsWithin(Duration.ofSeconds(8)).isEqualTo(Optional.empty());
        }
        finally {
            release.countDown();
        }
    }

    @Test
    void provider429IsNotRetriedAndTheOutputBudgetIsSent() throws Exception {
        var calls = new AtomicInteger();
        var body = new AtomicReference<String>();
        var received = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            received.countDown();
            byte[] error = "{\"error\":{\"message\":\"rate limited\",\"type\":\"rate_limit_exceeded\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, error.length);
            exchange.getResponseBody().write(error);
            exchange.close();
        });
        server.start();
        // the endpoint keeps its default of three SDK retries; Home's own transport must not use them
        var endpoint = new LlmProperties.Endpoint(LlmProperties.EndpointType.OPENAI, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key",
                Duration.ofSeconds(300), 3, null, null, null, List.of());
        var llmClientService = new LlmClientService(new LlmProperties(Map.of("local", endpoint), Map.of("welcome", PRESET), null), new JsonMapper(), ObservationRegistry.NOOP,
                new SimpleMeterRegistry(), List.of());
        try (var client = new SpringAiCourseHomeClient(new CourseHomeAiProperties(true, "LOCAL", "welcome"), llmClientService, new SimpleMeterRegistry())) {
            var result = client.generate(input);
            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(result).succeedsWithin(Duration.ofSeconds(8)).isEqualTo(Optional.empty());
            assertThat(calls).hasValue(1);
            assertThat(body.get()).contains("\"max_output_tokens\":160");
        }
        finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void successfulOutputIsReturned() {
        var model = model();
        when(model.call(any(Prompt.class))).thenReturn(response());
        try (var client = new SpringAiCourseHomeClient(model, PRESET, new SimpleMeterRegistry())) {
            assertThat(client.generate(input)).succeedsWithin(Duration.ofSeconds(8)).isEqualTo(Optional.of("Keep exploring one manageable step."));
        }
    }

    @Test
    void aLowerPresetLimitAndItsTokenFieldAreKept() {
        var model = model();
        when(model.call(any(Prompt.class))).thenReturn(response());
        var preset = new LlmProperties.Preset("local", "m", LlmProperties.Api.RESPONSES, null, null, null, null, 100, null, null, null);
        try (var client = new SpringAiCourseHomeClient(model, preset, new SimpleMeterRegistry())) {
            assertThat(client.generate(input)).succeedsWithin(Duration.ofSeconds(8));
        }
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxTokens()).isEqualTo(100);
            assertThat(options.getMaxCompletionTokens()).isNull();
        });
    }

    @Test
    void emptyModelChoicesProduceFallbackInsteadOfThrowing() {
        var model = model();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        try (var client = new SpringAiCourseHomeClient(model, PRESET, new SimpleMeterRegistry())) {
            assertThat(client.generate(input)).succeedsWithin(Duration.ofSeconds(8)).isEqualTo(Optional.empty());
        }
    }

    @Test
    void incompleteConfigurationCannotConstructATransport() {
        assertThatThrownBy(() -> new SpringAiCourseHomeClient(new CourseHomeAiProperties(false, null, null), mock(LlmClientService.class), new SimpleMeterRegistry()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("configuration is incomplete");
    }

    @Test
    void theInputBoundaryRejectsOversizedPrompts() {
        assertThat(new CourseHomeAiClient.Input("x".repeat(4096), "x".repeat(4096)).userPrompt()).hasSize(4096);
        assertThatThrownBy(() -> new CourseHomeAiClient.Input("x".repeat(8192), "x")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds its bound");
    }

    private static ChatModel model() {
        // a preset's model carries OpenAI options, into which the chat client merges the request options
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        return model;
    }

    private ChatResponse response() {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("Keep exploring one manageable step.").build())));
    }
}
