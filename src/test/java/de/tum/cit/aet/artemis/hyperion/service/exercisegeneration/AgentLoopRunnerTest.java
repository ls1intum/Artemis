package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;

/**
 * Deterministic unit test for the agent loop: no real LLM and no Docker. A mocked {@link ChatModel} returns a scripted sequence of responses and an in-memory fake
 * {@link InteractiveSandbox} captures the tool calls, so the test exercises the loop control, tool dispatch, budget cap, and cancellation in isolation.
 */
class AgentLoopRunnerTest {

    /** In-memory fake sandbox: write/read operate on a map, bash is a no-op success. Lets us assert the agent's tool calls deterministically. */
    private static final class FakeSandbox implements InteractiveSandbox {

        private final Map<String, String> files = new HashMap<>();

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "fake-session";
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            // Emulate the two operations the tools use: `cat <path>` and `sh -c "... base64 -d > <path>"`.
            if (command.length >= 2 && "cat".equals(command[0])) {
                String path = command[1];
                String content = files.getOrDefault(path, null);
                if (content == null) {
                    return new SandboxExecResult(1, "", "cat: " + path + ": No such file or directory", false);
                }
                return new SandboxExecResult(0, content, "", false);
            }
            // Any other command (mkdir/base64 write, bash) succeeds.
            return new SandboxExecResult(0, "ok", "", false);
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    private static ChatResponse toolCallResponse(String name, String arguments) {
        var toolCall = new AssistantMessage.ToolCall("call-1", "function", name, arguments);
        var message = AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void agentLoop_recoversFromUnknownToolCall_andContinues() {
        ChatModel chatModel = mock(ChatModel.class);
        // Turn 1 invents a tool the agent does not have (real models do this — observed in the end-to-end run); the loop must feed the error back and continue, then finish.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("apply_patch", "{\"path\":\"x\"}"), textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        // The unknown tool must NOT abort the run; the agent recovers and completes.
        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
    }

    @Test
    void agentLoop_normalizesLeakedHarmonyToolName_andDispatchesToTheRealTool() {
        ChatModel chatModel = mock(ChatModel.class);
        // Some model servers leak a harmony control token into the tool name (observed: "bash<|channel|>commentary"). Without normalization the name matches no registered tool and
        // the loop would thrash on tool-execution failures. With normalization it dispatches to "bash" and the run completes cleanly.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash<|channel|>commentary", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        // The transcript shows the normalized name, confirming dispatch went to the real tool (not the leaked one).
        assertThat(steps).contains("Turn 1: bash {\"command\":\"ls\"}");
    }

    @Test
    void sanitizeToolName_stripsHarmonyControlTokens() {
        assertThat(AgentLoopRunner.sanitizeToolName("bash<|channel|>commentary")).isEqualTo("bash");
        assertThat(AgentLoopRunner.sanitizeToolName("bash<|channel|>")).isEqualTo("bash");
        assertThat(AgentLoopRunner.sanitizeToolName("write_file")).isEqualTo("write_file");
        assertThat(AgentLoopRunner.sanitizeToolName("submit<|end|>")).isEqualTo("submit");
        assertThat(AgentLoopRunner.sanitizeToolName("  edit_file  ")).isEqualTo("edit_file");
    }

    @Test
    void describeToolCall_rendersFullPathFirstForFileTools_soTheClientCanParseIt() {
        ChatModel chatModel = mock(ChatModel.class);
        String longContent = "x".repeat(500);
        // Arguments deliberately put a large "content" BEFORE "path" (the model controls key order); the transcript line must still surface the full, untruncated path, because the
        // client's "files changed" view parses the path as the file tool's argument. This is the contract with generation-progress.model.ts.
        String args = "{\"content\":\"" + longContent + "\",\"path\":\"solution/src/de/tum/example/VeryLongClassNameThatExceedsTheTruncationLimit.java\"}";
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("write_file", args), textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(steps).contains("Turn 1: write_file solution/src/de/tum/example/VeryLongClassNameThatExceedsTheTruncationLimit.java");
    }

    @Test
    void describeToolCall_unescapesJsonEscapesInThePath() {
        ChatModel chatModel = mock(ChatModel.class);
        // The JSON path "a\\b\"c.java" decodes to the literal path a\b"c.java; the transcript must show that unescaped path (guards the replace-chain order in
        // extractJsonStringValue).
        String args = "{\"path\":\"a\\\\b\\\"c.java\",\"content\":\"x\"}";
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("write_file", args), textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(steps).contains("Turn 1: write_file a\\b\"c.java");
    }

    @Test
    void describeToolCall_keepsAPathWithSpacesIntact() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("write_file", "{\"path\":\"solution/My Notes.md\",\"content\":\"x\"}"), textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(steps).contains("Turn 1: write_file solution/My Notes.md");
    }

    @Test
    void agentLoop_repeatedToolFailures_endWithError() {
        ChatModel chatModel = mock(ChatModel.class);
        // Always invent an unknown tool -> after MAX_CONSECUTIVE_TOOL_FAILURES the loop gives up with an error rather than spinning forever.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("apply_patch", "{}"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 20, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
    }

    @Test
    void agentLoop_transientModelError_isRetriedThenSucceeds() {
        ChatModel chatModel = mock(ChatModel.class);
        // First call throws a transient endpoint error, the retry succeeds and finishes — a single hiccup must not abort the generation.
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 503 from endpoint")).thenReturn(textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
    }

    @Test
    void agentLoop_persistentModelError_endsWithError() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 500 from endpoint"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
    }

    @Test
    void agentLoop_submitTool_endsLoopImmediately() {
        ChatModel chatModel = mock(ChatModel.class);
        // Calling submit declares completion; the loop must end that very turn and hand off to the verifier, not wait for a further no-tool-call turn.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("submit", "{\"summary\":\"bubble sort done\"}"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(1);
    }

    @Test
    void agentLoop_callsToolThenStops_completesWithinBudget() {
        ChatModel chatModel = mock(ChatModel.class);
        // Turn 1: call bash; Turn 2: no tool calls -> done.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(2);
        assertThat(result.finalMessage()).isEqualTo("DONE");
    }

    @Test
    void agentLoop_neverStops_hitsBudget() {
        ChatModel chatModel = mock(ChatModel.class);
        // Always request a tool call -> the loop must stop at the budget.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 3, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        assertThat(result.turns()).isEqualTo(3);
    }

    @Test
    void agentLoop_cancellationRequested_stopsCooperatively() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> true, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
    }

    @Test
    void agentLoop_noChatModel_throws() {
        AgentLoopRunner runner = new AgentLoopRunner(List.of(), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> runner.run("system", "do it", tools, 5, () -> false, null, null))
                .withMessageContaining("No ChatModel");
    }

    @Test
    void agentLoop_invokesUsageSinkOncePerModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        // Turn 1: a tool call; Turn 2: no tool calls -> done. Two successful model calls, so the usage sink must see exactly two responses.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = new AgentLoopRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<ChatResponse> recorded = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, recorded::add, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(recorded).hasSize(2);
    }
}
