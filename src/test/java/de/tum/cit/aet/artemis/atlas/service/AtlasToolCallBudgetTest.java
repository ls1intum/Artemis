package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

import de.tum.cit.aet.artemis.atlas.config.AtlasAgentProperties;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;

class AtlasToolCallBudgetTest {

    private static final AtlasAgentProperties PROPERTIES = new AtlasAgentProperties("test", 0.0);

    @Test
    void sameToolMayRun48TimesThroughNativeAdvisor() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = modelReturning(new AtomicInteger(), round -> round <= 48 ? toolCallResponse("getExerciseContent") : textResponse("done"));
        ChatResponse response = newService(model).delegateOrchestratorRound("system", "run", OpenAiChatOptions.builder(), new HashMap<>(),
                ToolCallbackProvider.from(callback("getExerciseContent", calls, false)));
        assertThat(calls).hasValue(48);
        assertThat(response.getResult().getOutput().getText()).isEqualTo("done");
    }

    @Test
    void writesStopAt224WhileReadsAndCompletionRemainAvailable() {
        AtlasToolCallBudget budget = new AtlasToolCallBudget();
        AtomicInteger writes = new AtomicInteger();
        ToolCallback write = decorate(callback("assignExerciseToCompetency", writes, false), budget);
        for (int i = 0; i < 224; i++) {
            write.call("{}");
        }
        assertThat(write.call("{}")).contains("NOT EXECUTED", "WRAP UP NOW");
        assertThat(writes).hasValue(224);
        assertThat(budget.workBlocked()).isTrue();
        AtomicInteger reads = new AtomicInteger();
        assertThat(decorate(callback("getExerciseContent", reads, false), budget).call("{}")).contains("ok", "226/256");
        AtomicInteger completions = new AtomicInteger();
        decorate(callback("completeOrchestration", completions, false), budget).call("{}");
        assertThat(reads).hasValue(1);
        assertThat(completions).hasValue(1);
        assertThat(budget.exhausted()).isFalse();
    }

    @Test
    void lastSlotIsReservedForCompletion() {
        AtlasToolCallBudget budget = new AtlasToolCallBudget();
        ToolCallback read = decorate(callback("getExerciseContent", new AtomicInteger(), false), budget);
        for (int i = 0; i < 255; i++) {
            read.call("{}");
        }
        AtomicInteger completion = new AtomicInteger();
        decorate(callback("completeOrchestration", completion, false), budget).call("{}");
        assertThat(completion).hasValue(1);
        assertThat(budget.calls()).isEqualTo(256);
        assertThatThrownBy(() -> read.call("{}")).isInstanceOf(ToolCallLimitExceededException.class);
        assertThat(budget.calls()).isEqualTo(256);
    }

    @Test
    void workerCompletionCannotSpendTheMainOrchestratorsFinalSlot() {
        AtlasToolCallBudget budget = new AtlasToolCallBudget();
        ToolCallback read = decorate(callback("getExerciseContent", new AtomicInteger(), false), budget);
        for (int i = 0; i < 255; i++) {
            read.call("{}");
        }
        AtomicInteger completions = new AtomicInteger();
        ToolCallback workerCompletion = decorate(callback("completeWorkerTask", completions, false), budget);
        assertThatThrownBy(() -> workerCompletion.call("{}")).isInstanceOf(ToolCallLimitExceededException.class);
        assertThat(completions).hasValue(0);
        assertThat(budget.calls()).isEqualTo(255);
    }

    @Test
    void uncooperativeModelStopsAtHardLimitAndRetainsUsage() {
        Map<String, Object> context = new HashMap<>();
        ChatModel model = modelReturning(new AtomicInteger(), round -> toolCallResponseWithUsage("getExerciseContent"));
        ChatResponse response = newService(model).delegateOrchestratorRound("system", "run", OpenAiChatOptions.builder(), context,
                ToolCallbackProvider.from(callback("getExerciseContent", new AtomicInteger(), false)));
        assertThat(AtlasToolCallBudget.budgetForContext(context).calls()).isEqualTo(255);
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(256);
        assertThatThrownBy(() -> AtlasToolCallBudget.checkResponse(response, context)).isInstanceOf(AtlasToolCallBudget.LimitReachedException.class);
    }

    @Test
    void requestedLargeToolBatchCannotPerformWritesAfterWrapUp() {
        AtlasToolCallBudget budget = new AtlasToolCallBudget();
        AtomicInteger writes = new AtomicInteger();
        ToolCallback write = decorate(callback("assignExerciseToCompetency", writes, false), budget);
        IntStream.range(0, 300).parallel().forEach(i -> {
            try {
                write.call("{}");
            }
            catch (ToolCallLimitExceededException expected) {
            }
        });
        assertThat(writes.get()).isLessThanOrEqualTo(224);
        assertThat(budget.calls()).isEqualTo(255);
        assertThat(budget.exhausted()).isTrue();
    }

    @Test
    void parentAndWorkerShareRemainingBudget() {
        Map<String, Object> parent = new HashMap<>();
        AtlasToolCallBudget budget = AtlasToolCallBudget.budgetForContext(parent);
        Map<String, Object> worker = new HashMap<>(Map.of(AtlasToolCallBudget.CONTEXT_KEY, budget));
        assertThat(AtlasToolCallBudget.budgetForContext(worker)).isSameAs(budget);
        assertThat(AtlasToolCallBudget.budgetForContext(new HashMap<>())).isNotSameAs(budget);
        ToolCallback read = decorate(callback("getExerciseContent", new AtomicInteger(), false), budget);
        for (int i = 0; i < 224; i++) {
            read.call("{}");
        }
        AtomicInteger writes = new AtomicInteger();
        decorate(callback("assignExerciseToCompetency", writes, false), AtlasToolCallBudget.budgetForContext(worker)).call("{}");
        assertThat(writes).hasValue(0);
        assertThat(budget.instructions()).contains("225/256", "WRAP UP NOW");
    }

    @Test
    void blockedWorkCannotBecomeSuccessJustBecauseTheModelSaysDone() {
        Map<String, Object> context = new HashMap<>();
        AtlasToolCallBudget budget = AtlasToolCallBudget.budgetForContext(context);
        ToolCallback write = decorate(callback("assignExerciseToCompetency", new AtomicInteger(), false), budget);
        for (int i = 0; i < 225; i++) {
            write.call("{}");
        }
        assertThatThrownBy(() -> AtlasToolCallBudget.checkResponse(textResponse("Eight done; four remain."), context))
                .isInstanceOfSatisfying(AtlasToolCallBudget.LimitReachedException.class, ex -> assertThat(ex.summary()).contains("Eight done; four remain."));
    }

    @Test
    void indexReadStartedDuringActiveWorkCannotVerifyCompletion() throws Exception {
        AtlasToolCallBudget budget = new AtlasToolCallBudget();
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch allowWorkToFinish = new CountDownLatch(1);
        CountDownLatch indexStarted = new CountDownLatch(1);
        CountDownLatch allowIndexToFinish = new CountDownLatch(1);
        ToolCallback write = decorate(callback("assignExerciseToCompetency", () -> {
            workStarted.countDown();
            await(allowWorkToFinish);
            return "ok";
        }), budget);
        ToolCallback index = decorate(callback("listCompetencyIndex", () -> {
            indexStarted.countDown();
            await(allowIndexToFinish);
            return "{}";
        }), budget);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> work = executor.submit(() -> write.call("{}"));
            assertThat(workStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> staleIndex = executor.submit(() -> index.call("{}"));
            assertThat(indexStarted.await(5, TimeUnit.SECONDS)).isTrue();

            allowWorkToFinish.countDown();
            assertThat(work.get(5, TimeUnit.SECONDS)).contains("ok");
            allowIndexToFinish.countDown();
            assertThat(staleIndex.get(5, TimeUnit.SECONDS)).contains("atlasBudget");

            assertThatThrownBy(() -> budget.complete(true, "stale verification")).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("successful index refresh after the latest work");
            index.call("{}");
            budget.complete(true, "fresh verification");
            assertThat(budget.completion().message()).isEqualTo("fresh verification");
        }
        finally {
            allowWorkToFinish.countDown();
            allowIndexToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedCallbacksCountAndEvidenceContainsHashesInsteadOfArguments() {
        AtlasToolCallBudget budget = new AtlasToolCallBudget();
        ToolCallback callback = decorate(callback("failing", new AtomicInteger(), true), budget);
        assertThatThrownBy(() -> callback.call("private text")).isInstanceOf(IllegalStateException.class);
        assertThat(budget.calls()).isEqualTo(1);
        assertThat(budget.activity()).hasSize(1);
        assertThat(budget.activity().getFirst()).containsEntry("outcome", "exception");
        assertThat(budget.activity().toString()).doesNotContain("private text");
    }

    @Test
    void nestedExhaustionStopsParentWithoutAnotherProviderRound() {
        Map<String, Object> context = new HashMap<>();
        AtlasToolCallBudget budget = AtlasToolCallBudget.budgetForContext(context);
        ToolCallback reads = decorate(callback("getExerciseContent", new AtomicInteger(), false), budget);
        AtomicInteger modelCalls = new AtomicInteger();
        ToolCallback worker = new ToolCallback() {

            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("delegateWorker").description("nested worker").inputSchema("{}").build();
            }

            @Override
            public String call(String arguments) {
                try {
                    while (true) {
                        reads.call("{}");
                    }
                }
                catch (ToolCallLimitExceededException exhausted) {
                    return "worker could not finish";
                }
            }
        };
        ChatResponse response = newService(modelReturning(modelCalls, round -> toolCallResponse("delegateWorker"))).delegateOrchestratorRound("system", "run",
                OpenAiChatOptions.builder(), context, ToolCallbackProvider.from(worker));
        assertThat(modelCalls).hasValue(1);
        assertThat(budget.calls()).isEqualTo(255);
        assertThatThrownBy(() -> AtlasToolCallBudget.checkResponse(response, context)).isInstanceOf(AtlasToolCallBudget.LimitReachedException.class);
    }

    @Test
    void extractionIsSharedWithinInvocationButNotAcrossInvocations() {
        Map<String, Object> context = new HashMap<>();
        AtlasToolCallBudget.budgetForContext(context);
        ToolContext tools = new ToolContext(context);
        AtomicInteger extracts = new AtomicInteger();
        java.util.function.Supplier<ExtractedContentDTO> extract = () -> {
            extracts.incrementAndGet();
            return new ExtractedContentDTO("title", "text", Map.of());
        };
        assertThat(AtlasToolCallBudget.content(tools, "exercise:1", extract)).isSameAs(AtlasToolCallBudget.content(tools, "exercise:1", extract));
        assertThat(extracts).hasValue(1);
        Map<String, Object> other = new HashMap<>();
        AtlasToolCallBudget.budgetForContext(other);
        AtlasToolCallBudget.content(new ToolContext(other), "exercise:1", extract);
        assertThat(extracts).hasValue(2);
    }

    private static ToolCallback decorate(ToolCallback callback, AtlasToolCallBudget budget) {
        return AtlasToolCallBudget.decorate(ToolCallbackProvider.from(callback), budget).getToolCallbacks()[0];
    }

    private static AtlasAgentDelegationService newService(ChatModel chatModel) {
        return new AtlasAgentDelegationService(ChatClient.create(chatModel), mock(AtlasPromptTemplateService.class), null, PROPERTIES);
    }

    private static ChatModel modelReturning(AtomicInteger calls, java.util.function.IntFunction<ChatResponse> responseFactory) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> responseFactory.apply(calls.incrementAndGet()));
        return model;
    }

    private static ToolCallback callback(String name, AtomicInteger calls, boolean fail) {
        return callback(name, () -> {
            calls.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("callback failed");
            }
            return "ok";
        });
    }

    private static ToolCallback callback(String name, Supplier<String> invocation) {
        ToolDefinition definition = ToolDefinition.builder().name(name).description("test tool").inputSchema("{}").build();
        ToolMetadata metadata = DefaultToolMetadata.builder().returnDirect(false).build();
        return new ToolCallback() {

            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return metadata;
            }

            @Override
            public String call(String arguments) {
                return invocation.get();
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent callback ordering");
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static ChatResponse toolCallResponse(String name) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(new ToolCall("id", "function", name, "{}"))).build())));
    }

    private static ChatResponse toolCallResponseWithUsage(String name) {
        Generation generation = new Generation(AssistantMessage.builder().content("").toolCalls(List.of(new ToolCall("id", "function", name, "{}"))).build(),
                ChatGenerationMetadata.builder().build());
        return new ChatResponse(List.of(generation), ChatResponseMetadata.builder().usage(new Usage() {

            @Override
            public Integer getPromptTokens() {
                return 1;
            }

            @Override
            public Integer getCompletionTokens() {
                return 1;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        }).build());
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
