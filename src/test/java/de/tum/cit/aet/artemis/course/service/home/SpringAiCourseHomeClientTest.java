package de.tum.cit.aet.artemis.course.service.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.setup.OpenAiSetup;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.sun.net.httpserver.HttpServer;

import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class SpringAiCourseHomeClientTest {

    private final CourseHomeAiClient.Input input = new CourseHomeAiClient.Input("Encourage learning.", "Published context.");

    @Test
    void fourBusyWorkersRejectFurtherWorkInsteadOfQueuing() throws Exception {
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await();
            return response();
        });
        try (var client = new SpringAiCourseHomeClient(model, () -> {
        }, () -> {
        }, new SimpleMeterRegistry())) {
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
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenAnswer(i -> {
            release.await();
            return response();
        });
        try (var client = new SpringAiCourseHomeClient(model, () -> {
        }, () -> {
        }, new SimpleMeterRegistry())) {
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
        server.createContext("/v1/chat/completions", exchange -> {
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
        try (var client = new SpringAiCourseHomeClient(
                new CourseHomeAiProperties(true, "LOCAL", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key", "test-model"), new SimpleMeterRegistry())) {
            var result = client.generate(input);
            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(result).succeedsWithin(Duration.ofSeconds(8)).isEqualTo(Optional.empty());
            assertThat(calls).hasValue(1);
            assertThat(body.get()).contains("\"max_completion_tokens\":160");
        }
        finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void successfulOutputIsReturnedAndBothFailingTransportsAreClosed() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response());
        var closes = new AtomicInteger();
        AutoCloseable failing = () -> {
            closes.incrementAndGet();
            throw new IllegalStateException("transport close failed");
        };
        var meters = new SimpleMeterRegistry();
        try (var client = new SpringAiCourseHomeClient(model, failing, failing, meters)) {
            assertThat(client.generate(input)).succeedsWithin(Duration.ofSeconds(8)).isEqualTo(Optional.of("Keep exploring one manageable step."));
        }
        assertThat(closes).hasValue(2);
        assertThat(meters.counter("artemis.course.home.ai.request", "outcome", "close_failure").count()).isEqualTo(2);
    }

    @Test
    void emptyModelChoicesProduceFallbackInsteadOfThrowing() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        try (var client = new SpringAiCourseHomeClient(model, () -> {
        }, () -> {
        }, new SimpleMeterRegistry())) {
            assertThat(client.generate(input)).succeedsWithin(Duration.ofSeconds(8)).isEqualTo(Optional.empty());
        }
    }

    @Test
    void incompleteConfigurationCannotConstructATransport() {
        assertThatThrownBy(() -> new SpringAiCourseHomeClient(new CourseHomeAiProperties(false, null, null, null, null), new SimpleMeterRegistry()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("configuration is incomplete");
    }

    @Test
    void theInputBoundaryRejectsOversizedPrompts() {
        assertThat(new CourseHomeAiClient.Input("x".repeat(4096), "x".repeat(4096)).userPrompt()).hasSize(4096);
        assertThatThrownBy(() -> new CourseHomeAiClient.Input("x".repeat(8192), "x")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeds its bound");
    }

    @Test
    void asynchronousTransportConstructionFailureClosesTheSynchronousTransport() {
        var sync = mock(OpenAIClient.class);
        try (var setup = mockStatic(OpenAiSetup.class, invocation -> {
            if (invocation.getMethod().getName().equals("setupSyncClient"))
                return sync;
            if (invocation.getMethod().getName().equals("setupAsyncClient"))
                throw new IllegalStateException("async construction failed");
            return CALLS_REAL_METHODS.answer(invocation);
        })) {
            assertThatThrownBy(() -> new SpringAiCourseHomeClient(configured(), new SimpleMeterRegistry())).isInstanceOf(IllegalStateException.class)
                    .hasMessage("async construction failed");
            verify(sync).close();
        }
    }

    @Test
    void modelConstructionFailureClosesBothAllocatedTransports() {
        var sync = mock(OpenAIClient.class);
        var async = mock(OpenAIClientAsync.class);
        try (var setup = mockStatic(OpenAiSetup.class, invocation -> {
            if (invocation.getMethod().getName().equals("setupSyncClient"))
                return sync;
            if (invocation.getMethod().getName().equals("setupAsyncClient"))
                return async;
            return CALLS_REAL_METHODS.answer(invocation);
        }); var models = mockStatic(OpenAiChatModel.class)) {
            models.when(OpenAiChatModel::builder).thenThrow(new IllegalStateException("model construction failed"));
            assertThatThrownBy(() -> new SpringAiCourseHomeClient(configured(), new SimpleMeterRegistry())).isInstanceOf(IllegalStateException.class)
                    .hasMessage("model construction failed");
            verify(sync).close();
            verify(async).close();
        }
    }

    private CourseHomeAiProperties configured() {
        return new CourseHomeAiProperties(true, "LOCAL", "http://127.0.0.1:1/v1", "test-key", "test-model");
    }

    private ChatResponse response() {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("Keep exploring one manageable step.").build())));
    }
}
