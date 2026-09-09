package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;

class AgentLoopRunnerTest {

    @ParameterizedTest
    @CsvSource({ "41, 1", "40, 4" })
    void validToolsRemainAvailableThroughoutTheConfiguredTurnBudget(int toolTurns, int callsPerTurn) {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger turn = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = turn.incrementAndGet();
            if (current > toolTurns) {
                return text("finished");
            }
            var toolCalls = IntStream.range(0, callsPerTurn).mapToObj(index -> new AssistantMessage.ToolCall(current + "-" + index, "function", "write", "{}")).toList();
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(toolCalls).build())));
        });
        var settings = new HyperionGenerationSettings("smoke", "Smoke", 60, Duration.ofMinutes(90), 3_000_000, true, "CONTINUOUS", 200_000, null, false, false);
        RecordingTools tools = new RecordingTools();

        var result = runner(model).forSettings(settings).run("system", "brief", tools, settings.maxTurns(), () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(toolTurns + 1);
        assertThat(tools.actions).hasSize(toolTurns * callsPerTurn).containsOnly("write");
    }

    @Test
    void naturalCompletionPreservesConversationAndAccountsForCall() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(text("finished"));
        ProviderUsageSink usage = mock(ProviderUsageSink.class);

        var session = runner(model).runSession("system", null, "brief", new RecordingTools(), 4, () -> false, usage, null);

        assertThat(session.result()).isEqualTo(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, "finished"));
        assertThat(session.conversation()).hasSize(2);
        assertThat(session.conversation().getLast().getText()).isEqualTo("finished");
        verify(usage).recordTurn();
        verify(usage).accept(any(ChatResponse.class));
    }

    @Test
    void mixedSubmitBatchExecutesAllToolsBeforeEnding() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(calls("stop", "submit", "edit", "write"));
        RecordingTools tools = new RecordingTools();

        var session = runner(model).runSession("system", null, "brief", tools, 4, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(tools.actions).containsExactly("submit", "write");
        var results = (ToolResponseMessage) session.conversation().getLast();
        assertThat(results.getResponses()).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("stop", "edit");
        verify(model).call(any(Prompt.class));
    }

    @Test
    void submitVetoContinuesWithinSameSession() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(calls("stop", "submit"), calls("fix", "write"), text("ready"));
        RecordingTools tools = new RecordingTools();
        tools.veto = true;

        var result = runner(model).run("system", "brief", tools, 4, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(3);
        assertThat(tools.actions).containsExactly("submit", "write");
    }

    @Test
    void truncatedToolResponseDoesNotExecuteEvenValidArguments() {
        ChatModel model = mock(ChatModel.class);
        AssistantMessage output = calls("edit", "write").getResult().getOutput();
        var truncated = new ChatResponse(List.of(new Generation(output, ChatGenerationMetadata.builder().finishReason("length").build())));
        when(model.call(any(Prompt.class))).thenReturn(truncated, text("finished"));
        RecordingTools tools = new RecordingTools();

        var result = runner(model).run("system", "brief", tools, 4, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(tools.actions).isEmpty();
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions()).anySatisfy(message -> {
            if (message instanceof ToolResponseMessage response) {
                assertThat(response.getResponses().getFirst().responseData()).contains("not executed");
            }
            else {
                org.assertj.core.api.Assertions.fail("Expected a tool response");
            }
        });
    }

    @Test
    void turnBudgetStopsRepeatedToolsWithoutFabricatingCompletion() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(calls("edit", "write"));
        RecordingTools tools = new RecordingTools();

        var result = runner(model).run("system", "brief", tools, 2, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        assertThat(result.turns()).isEqualTo(2);
        assertThat(tools.actions).containsExactly("write", "write");
    }

    @Test
    void cancellationBeforeCallDoesNotSpend() {
        ChatModel model = mock(ChatModel.class);

        var result = runner(model).run("system", "brief", new RecordingTools(), 4, () -> true, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void providerResponseAfterCancellationCannotExecuteTools() {
        ChatModel model = mock(ChatModel.class);
        AtomicBoolean cancelled = new AtomicBoolean();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            cancelled.set(true);
            return calls("edit", "write");
        });
        RecordingTools tools = new RecordingTools();

        var result = runner(model).run("system", "brief", tools, 4, cancelled::get, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        assertThat(tools.actions).isEmpty();
    }

    private static AgentLoopRunner runner(ChatModel model) {
        return new AgentLoopRunner(List.of(model), 128_000, Duration.ZERO, ProviderFailureCooldown.disabled());
    }

    private static ChatResponse text(String value) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(value))));
    }

    private static ChatResponse calls(String... idAndName) {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < idAndName.length; i += 2) {
            calls.add(new AssistantMessage.ToolCall(idAndName[i], "function", idAndName[i + 1], "{}"));
        }
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(calls).build())));
    }

    static class RecordingTools implements SubmitVetoAware {

        final List<String> actions = new ArrayList<>();

        boolean veto;

        @Tool(description = "Record an edit")
        public String write() {
            actions.add("write");
            return "written";
        }

        @Tool(description = "Submit the current workspace")
        public String submit() {
            actions.add("submit");
            return veto ? "Fix the exercise first" : "submitted";
        }

        @Override
        public boolean consumeSubmitVeto() {
            boolean result = veto;
            veto = false;
            return result;
        }

        @Override
        public boolean isSandboxSessionTerminated() {
            return false;
        }
    }
}
