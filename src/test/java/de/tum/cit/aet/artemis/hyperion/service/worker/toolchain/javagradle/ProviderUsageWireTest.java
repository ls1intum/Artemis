package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.ai.tool.annotation.Tool;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate.Kind;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageTransport;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.SubmitVetoAware;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

class ProviderUsageWireTest {

    @Test
    void omittedWireUsageStopsBeforeToolsOrAnotherRequest() {
        String completion = """
                {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                 "tool_calls":[{"id":"edit","type":"function","function":{"name":"write","arguments":"{}"}}]},
                 "finish_reason":"tool_calls"}]}
                """;
        try (var wire = fixture(completion)) {
            var events = new ArrayList<ProviderUsageUpdate>();
            var recorder = spy(new WorkerUsageRecorder(1_000, 1, true, events::add));
            var runner = new AgentLoopRunner(List.of(wire.model()), 128_000, Duration.ofMinutes(5), ProviderFailureCooldown.disabled());
            var tools = new RecordingTools();

            var result = runner.run("system", "brief", tools, 4, () -> false, recorder, null);

            var responses = ArgumentCaptor.forClass(ChatResponse.class);
            verify(recorder).accept(responses.capture());
            var response = responses.getValue();
            assertThat(response.getMetadata().getUsage()).isInstanceOf(EmptyUsage.class);
            assertThat(response.getResult().getOutput().getToolCalls()).singleElement().satisfies(call -> assertThat(call.name()).isEqualTo("write"));
            assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
            assertThat(tools.writes).isZero();
            assertThat(wire.requests()).hasValue(1);
            assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
            assertThat(recorder.snapshot().modelCalls()).isZero();
            assertThat(events).extracting(ProviderUsageUpdate::kind).containsExactly(Kind.TURN, Kind.UNCERTAIN);
        }
    }

    @Test
    void suppliedZeroWireCountsAreNotMissingUsage() {
        try (var wire = fixture(textCompletion("{\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0}"))) {
            var response = wire.model().call(new Prompt("test"));

            assertThat(response.getMetadata().getUsage()).isNotInstanceOf(EmptyUsage.class);
            var call = ProviderUsageTransport.capture(response);
            assertThat(call.inputTokens()).isZero();
            assertThat(call.outputTokens()).isZero();
            var recorder = new WorkerUsageRecorder(1_000, 1, true, ignored -> {
            });
            recorder.accept(response);
            assertThat(recorder.accountingState()).isEqualTo(AccountingState.COMPLETE);
            assertThat(recorder.snapshot().modelCalls()).isEqualTo(1);
            assertThat(wire.requests()).hasValue(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void cachedWireCountsAreOptional(boolean cacheReported) {
        String cache = cacheReported ? ",\"prompt_tokens_details\":{\"cached_tokens\":3}" : "";
        try (var wire = fixture(textCompletion("{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12" + cache + "}"))) {
            var response = wire.model().call(new Prompt("test"));
            var call = ProviderUsageTransport.capture(response);

            assertThat(call.inputTokens()).isEqualTo(10);
            assertThat(call.outputTokens()).isEqualTo(2);
            assertThat(call.cachedInputTokens()).isEqualTo(cacheReported ? 3L : null);
            var recorder = new WorkerUsageRecorder(1_000, 1, true, ignored -> {
            });
            recorder.accept(response);
            assertThat(recorder.accountingState()).isEqualTo(AccountingState.COMPLETE);
            assertThat(recorder.snapshot().cachedInputTokensComplete()).isEqualTo(cacheReported);
            assertThat(wire.requests()).hasValue(1);
        }
    }

    private static String textCompletion(String usage) {
        return """
                {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"finished"},"finish_reason":"stop"}],
                 "usage":%s}
                """.formatted(usage);
    }

    private static WireFixture fixture(String completion) {
        var requests = new AtomicInteger();
        // The application interceptor returns before proceed: no DNS, socket, server or provider is used.
        OpenAiHttpClientBuilderCustomizer interceptor = builder -> builder.interceptor(chain -> {
            requests.incrementAndGet();
            assertThat(chain.request().url().encodedPath()).isEqualTo("/v1/chat/completions");
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(completion, MediaType.get("application/json"))).build();
        });
        var client = OpenAiSetup.setupSyncClient("https://example.invalid/v1", "test-key", null, null, null, null, false, false, null, Duration.ofSeconds(2), 0, null, Map.of(),
                ObservationRegistry.NOOP, null, List.of(interceptor));
        var async = OpenAiSetup.setupAsyncClient("https://example.invalid/v1", "test-key", null, null, null, null, false, false, null, Duration.ofSeconds(2), 0, null, Map.of(),
                ObservationRegistry.NOOP, null, List.of(interceptor));
        var model = OpenAiChatModel.builder().openAiClient(client).openAiClientAsync(async).options(OpenAiChatOptions.builder().model("test-model").build()).build();
        return new WireFixture(client, async, model, requests);
    }

    private record WireFixture(OpenAIClient client, OpenAIClientAsync async, OpenAiChatModel model, AtomicInteger requests) implements AutoCloseable {

        @Override
        public void close() {
            try {
                client.close();
            }
            finally {
                async.close();
            }
        }
    }

    static class RecordingTools implements SubmitVetoAware {

        private int writes;

        @Tool(description = "Record an edit")
        public String write() {
            writes++;
            return "written";
        }

        @Override
        public boolean consumeSubmitVeto() {
            return false;
        }

        @Override
        public boolean isSandboxSessionTerminated() {
            return false;
        }
    }
}
