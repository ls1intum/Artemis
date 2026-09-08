package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;

class AgentLoopToolFailureTest {

    @Test
    void leakedControlSuffixStillDispatchesTheRealTool() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(call("write_file<|channel|>commentary"), text());
        var tools = spy(new RejectedTools());

        var result = runner(model).run("system", "brief", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(tools).write();
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void unknownToolBatchAnswersEveryCallBeforeRetrying() {
        ChatModel model = mock(ChatModel.class);
        var unknown = new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("one", "function", "unknown", "{}"), new AssistantMessage.ToolCall("two", "function", "missing", "{}")))
                .build())));
        when(model.call(any(Prompt.class))).thenReturn(unknown, text());

        var result = runner(model).run("system", "brief", new RejectedTools(), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions().stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream()).toList()).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("one", "two");
    }

    @Test
    void repeatedUnknownToolsStopBeforeTheTurnBudget() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(call("unknown"));

        var result = runner(model).run("system", "brief", new RejectedTools(), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.turns()).isEqualTo(5);
        verify(model, times(5)).call(any(Prompt.class));
    }

    @Test
    void repeatedRejectedWritesStopBeforeTheTurnBudget() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(call("write_file"));

        var result = runner(model).run("system", "brief", new RejectedTools(), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.turns()).isEqualTo(5);
        verify(model, times(5)).call(any(Prompt.class));
    }

    @Test
    void recoverableFileRejectionsKeepTheSessionAliveAndProvideRepairGuidance() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse rejected = call("search");
        when(model.call(any(Prompt.class))).thenReturn(rejected, rejected, rejected, rejected, rejected, rejected, text());

        var result = runner(model).run("system", "brief", new RejectedTools(), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(7)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(2).getContents()).contains("Two file-tool actions were rejected", "Search accepts only one-line text");
    }

    @Test
    void sandboxLossIsTerminalRatherThanAModelCorrectableToolError() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(call("bash"));

        var result = runner(model).run("system", "brief", new RejectedTools(), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.turns()).isEqualTo(1);
        verify(model).call(any(Prompt.class));
    }

    private static AgentLoopRunner runner(ChatModel model) {
        return new AgentLoopRunner(List.of(model), 128_000, Duration.ZERO, ProviderFailureCooldown.disabled());
    }

    private static ChatResponse call(String name) {
        return new ChatResponse(
                List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall("call", "function", name, "{}"))).build())));
    }

    private static ChatResponse text() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
    }

    static class RejectedTools {

        @Tool(name = "write_file", description = "Write a file")
        public String write() {
            return "ERROR: protected path";
        }

        @Tool(description = "Search a file")
        public String search() {
            return "ERROR: invalid search";
        }

        @Tool(description = "Execute a command")
        public String bash() {
            throw new SandboxUnavailableException("Sandbox stopped", new IllegalStateException("Container no longer exists"));
        }
    }
}
