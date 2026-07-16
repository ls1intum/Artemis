package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.UnauthorizedException;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;

class AgentLoopRunnerTest {

    private static AgentLoopRunner newTestRunner(List<ChatModel> chatModels, int contextWindowTokens) {
        return newTestRunner(chatModels, contextWindowTokens, Duration.ofMinutes(5), new TestProviderFailureCooldown());
    }

    private static AgentLoopRunner newTestRunner(List<ChatModel> chatModels, int contextWindowTokens, Duration cooldown) {
        return newTestRunner(chatModels, contextWindowTokens, cooldown, new TestProviderFailureCooldown());
    }

    private static AgentLoopRunner newTestRunner(List<ChatModel> chatModels, int contextWindowTokens, Duration cooldown, ProviderFailureCooldown providerFailureCooldown) {
        return new AgentLoopRunner(chatModels, contextWindowTokens, cooldown, providerFailureCooldown);
    }

    /** In-memory fake sandbox: write/read operate on a map, bash is a no-op success. Lets us assert the agent's tool calls deterministically. */
    private static final class FakeSandbox implements InteractiveSandbox {

        private final Map<String, String> files = new HashMap<>();

        private final List<String> execCommands = new ArrayList<>();

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "fake-session";
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            execCommands.add(String.join(" ", command));
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
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("apply_patch", "{\"path\":\"x\"}"), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
    }

    @Test
    void agentLoop_scrubsHarmonyTokensFromModelResponses() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE<|end|>"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.finalMessage()).isEqualTo("DONE");
    }

    @Test
    void agentLoop_backsOffAndRetriesTransientModelFailures_thenRecovers() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new OpenAIIoException("read timed out")).thenReturn(textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setModelCallRetryTimingForTests(0L, 0L); // no real backoff waits in the test
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();
        List<ChatResponse> recorded = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, recorded::add, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        verify(chatModel, times(2)).call(any(Prompt.class));
        assertThat(recorded).hasSize(1);
        assertThat(steps).contains("The AI service is temporarily unavailable. Retrying.").noneMatch(step -> step.contains("read timed out"));
    }

    @Test
    void agentLoop_nonTransient4xx_failsFastWithoutRetrying() {
        ChatModel chatModel = mock(ChatModel.class);
        BadRequestException badRequest = BadRequestException.builder().headers(Headers.builder().build()).build();
        when(chatModel.call(any(Prompt.class))).thenThrow(badRequest);

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setModelCallRetryTimingForTests(0L, 0L);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();
        List<ChatResponse> recorded = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, recorded::add, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(recorded).isEmpty();
        verify(chatModel, times(1)).call(any(Prompt.class)); // fail fast: a deterministic 4xx is never retried
        assertThat(steps).contains("The AI service could not complete the request.");
    }

    @Test
    void isRetryable_classifiesTransientAndDeterministicFailures() {
        assertThat(AgentLoopRunner.isRetryable(new OpenAIIoException("read timed out"))).isTrue();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("GPU endpoint returned HTTP 429: too many requests"))).isTrue();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("GPU endpoint returned HTTP 500"))).isTrue();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("connection reset by peer"))).isTrue();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("wrapped", new OpenAIIoException("timeout")))).isTrue();
        assertThat(AgentLoopRunner.isRetryable(BadRequestException.builder().headers(Headers.builder().build()).build())).isFalse();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("GPU endpoint returned HTTP 401: unauthorized"))).isFalse();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("HTTP 422 unprocessable entity"))).isFalse();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("HTTP 408 request timeout"))).isTrue();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("HTTP 409 conflict"))).isTrue();
        assertThat(AgentLoopRunner.isRetryable(new RuntimeException("HTTP 429 insufficient_quota: exceeded your current quota"))).isFalse();
    }

    @Test
    void agentLoop_quotaExhaustionFailsFastAndActivatesProviderFailureCooldown() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 429 insufficient_quota: exceeded your current quota"));
        TestProviderFailureCooldown cooldown = new TestProviderFailureCooldown();

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000, Duration.ofMinutes(5), cooldown);
        runner.setModelCallRetryTimingForTests(0L, 0L);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class));

        ChatModel secondChatModel = mock(ChatModel.class);
        AgentLoopRunner secondRunner = newTestRunner(List.of(secondChatModel), 128_000, Duration.ofMinutes(5), cooldown);
        AgentLoopResult secondResult = secondRunner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(secondResult.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(secondChatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_plainRateLimitDoesNotActivateProviderFailureCooldown() {
        ChatModel rateLimitedModel = mock(ChatModel.class);
        when(rateLimitedModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 429 rate_limit_exceeded: too many requests"));
        TestProviderFailureCooldown cooldown = new TestProviderFailureCooldown();

        AgentLoopRunner runner = newTestRunner(List.of(rateLimitedModel), 128_000, Duration.ofMinutes(5), cooldown);
        runner.setModelCallRetryTimingForTests(0L, 0L);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(rateLimitedModel, times(2)).call(any(Prompt.class));

        ChatModel secondChatModel = mock(ChatModel.class);
        when(secondChatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));
        AgentLoopRunner secondRunner = newTestRunner(List.of(secondChatModel), 128_000, Duration.ofMinutes(5), cooldown);
        AgentLoopResult secondResult = secondRunner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(secondResult.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(secondChatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_modelNotFoundActivatesProviderFailureCooldown() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 404 model_not_found: requested model does not exist"));
        TestProviderFailureCooldown cooldown = new TestProviderFailureCooldown();

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000, Duration.ofMinutes(5), cooldown);
        runner.setModelCallRetryTimingForTests(0L, 0L);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class));

        ChatModel secondChatModel = mock(ChatModel.class);
        AgentLoopRunner secondRunner = newTestRunner(List.of(secondChatModel), 128_000, Duration.ofMinutes(5), cooldown);
        AgentLoopResult secondResult = secondRunner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(secondResult.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(secondChatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_typedUnauthorizedFailureActivatesProviderFailureCooldown() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(UnauthorizedException.builder().headers(Headers.builder().build()).build());
        TestProviderFailureCooldown cooldown = new TestProviderFailureCooldown();

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000, Duration.ofMinutes(5), cooldown);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class));

        ChatModel secondChatModel = mock(ChatModel.class);
        AgentLoopRunner secondRunner = newTestRunner(List.of(secondChatModel), 128_000, Duration.ofMinutes(5), cooldown);
        AgentLoopResult secondResult = secondRunner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(secondResult.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(secondChatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_cancellationDuringRetryBackoffReportsCancelled() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("GPU endpoint returned HTTP 503"));
        java.util.concurrent.atomic.AtomicInteger cancellationPolls = new java.util.concurrent.atomic.AtomicInteger();

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setModelCallRetryTimingForTests(1L, 1L);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> cancellationPolls.incrementAndGet() > 2, null,
                null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_emptyResponse_isReSampled() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse(""), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setModelCallRetryTimingForTests(0L, 0L);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();
        List<ChatResponse> recorded = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, recorded::add, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        verify(chatModel, times(2)).call(any(Prompt.class)); // empty sample re-drawn once, second is usable
        assertThat(recorded).hasSize(2); // every billable provider response is accounted, including the discarded empty sample
        assertThat(steps).contains("Model returned an empty response; retrying.");
    }

    @Test
    void agentLoop_repeatedEmptyResponsesReportErrorInsteadOfSilentCompletion() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse(""));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setModelCallRetryTimingForTests(0L, 0L);
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(2)).call(any(Prompt.class));
        assertThat(steps).contains("The AI service returned no usable response.");
    }

    @Test
    void agentLoop_interruptedDuringEmptyResponseBackoff_reportsError() {
        ChatModel chatModel = mock(ChatModel.class);
        // A cancel lands while the empty-response re-sample is backing off: the interrupt must surface as ERROR, not be papered over by handing the benign empty response to the
        // loop as a COMPLETED. Symmetric with the error-retry path, which also bails to ERROR on a mid-backoff interrupt.
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return textResponse("");
        });

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setModelCallRetryTimingForTests(50L, 50L); // non-zero so the backoff actually sleeps and observes the interrupt
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class)); // the interrupt stops the re-sample ladder instead of returning the empty response as a completion
        // The interrupt flag was restored by the backoff (honouring the interrupt); clear it here so it does not leak into other tests.
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void agentLoop_givesUpAfterTheBoundedOuterAttemptsFail_andReportsError() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("GPU endpoint returned HTTP 500"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setModelCallRetryTimingForTests(0L, 0L);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(2)).call(any(Prompt.class));
        assertThat(steps).contains("The AI service could not complete the request.").noneMatch(step -> step.contains("GPU endpoint"));
    }

    @Test
    void agentLoop_normalizesLeakedHarmonyToolName_andDispatchesToTheRealTool() {
        ChatModel chatModel = mock(ChatModel.class);
        // Some model servers leak a harmony control token into the tool name (observed: "bash<|channel|>commentary"). Without normalization the name matches no registered tool and
        // the loop would thrash on tool-execution failures. With normalization it dispatches to "bash" and the run completes cleanly.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash<|channel|>commentary", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        assertThat(steps).contains("Running a workspace command.").noneMatch(step -> step.contains("{\"command\""));
    }

    @Test
    void agentLoop_submitAlongsideAnotherToolCall_executesTheBatchThenEnds() {
        ChatModel chatModel = mock(ChatModel.class);
        var bash = new AssistantMessage.ToolCall("call-bash", "function", "bash", "{\"command\":\"ls\"}");
        var submit = new AssistantMessage.ToolCall("call-submit", "function", "submit", "{}");
        var message = AssistantMessage.builder().content("").toolCalls(List.of(bash, submit)).build();
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(message))));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        FakeSandbox sandbox = new FakeSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "fake-session");
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(1);
        assertThat(steps).filteredOn("Submitting the exercise for verification."::equals).hasSize(1);
        assertThat(sandbox.execCommands).as("the co-requested bash command was executed before the loop ended").anyMatch(command -> command.contains("ls"));
    }

    @Test
    void agentLoop_appendsTheBudgetPressureNudge_intoThePromptOfTheFinalAllowedTurn() {
        ChatModel chatModel = mock(ChatModel.class);
        // The model keeps calling a tool and never submits. With maxTurns=2, the loop must inject the budget-pressure nudge AFTER rebuilding the conversation from turn 1's tool
        // result, so it actually reaches the model on turn 2. The source explicitly warns the nudge would be DISCARDED if appended before that rebuild — this pins the ordering.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        runner.run("system", "do it", tools, 2, () -> false, null, null);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        String finalTurnPrompt = promptCaptor.getAllValues().get(1).getInstructions().stream().map(message -> message.getText()).collect(Collectors.joining("\n"));
        assertThat(finalTurnPrompt).as("the budget-pressure nudge reached the model on the final allowed turn (not discarded by the history rebuild)")
                .contains("close to the step limit");
    }

    @Test
    void agentLoop_usesTheModernFallbackTokenParameter() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 1, () -> false, null, null);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxCompletionTokens()).isEqualTo(16_384);
            assertThat(options.getMaxTokens()).isNull();
        });
    }

    @Test
    void agentLoop_preservesModernProviderOptions() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(
                OpenAiChatOptions.builder().maxCompletionTokens(12_345).reasoningEffort("medium").serviceTier("priority").customHeaders(Map.of("X-Test", "value")).build());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 1, () -> false, null, null);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxCompletionTokens()).isEqualTo(12_345);
            assertThat(options.getMaxTokens()).isNull();
            assertThat(options.getReasoningEffort()).isEqualTo("medium");
            assertThat(options.getServiceTier()).isEqualTo("priority");
            assertThat(options.getCustomHeaders()).containsEntry("X-Test", "value");
        });
    }

    @Test
    void agentLoop_clampsConfiguredOutputToTheRemainingContext() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().maxCompletionTokens(30_000).build());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 32_000);
        runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 1, () -> false, null, null);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class,
                options -> assertThat(options.getMaxCompletionTokens()).isGreaterThan(20_000).isLessThan(30_000));
    }

    @Test
    void agentLoop_preservesTheLegacyConfiguredTokenParameter() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().maxTokens(1_234).build());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.run("system", "do it", new SandboxAgentTools(new FakeSandbox(), "fake-session"), 1, () -> false, null, null);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxTokens()).isEqualTo(1_234);
            assertThat(options.getMaxCompletionTokens()).isNull();
        });
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
    void toolProgress_namesTheUpdatedFileWithoutExposingFileContent() {
        ChatModel chatModel = mock(ChatModel.class);
        String longContent = "x".repeat(500);
        String unsafePath = "solution/\\n\\u" + "001B\u2028\u2029\u202E" + "p".repeat(1_000) + ".java";
        String args = "{\"content\":\"" + longContent + "\",\"path\":\"" + unsafePath + "\"}";
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("write_file", args), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        String fileProgress = steps.stream().filter(step -> step.startsWith("Working on solution/")).findFirst().orElseThrow();
        assertThat(fileProgress).hasSizeLessThanOrEqualTo(180).doesNotContain(longContent, "\n", "\u001B", "\u2028", "\u2029", "\u202E").endsWith("….");
    }

    @Test
    void toolProgress_unescapesJsonEscapesInThePath() {
        ChatModel chatModel = mock(ChatModel.class);
        // The JSON path "a\\b\"c.java" decodes to the literal path a\b"c.java; the transcript must show that unescaped path (guards the replace-chain order in
        // extractJsonStringValue).
        String args = "{\"path\":\"a\\\\b\\\"c.java\",\"content\":\"x\"}";
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("write_file", args), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(steps).contains("Working on a\\b\"c.java.");
    }

    @Test
    void agentLoop_repeatedToolFailures_endWithError() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("apply_patch", "{}"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 20, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
    }

    @Test
    void agentLoop_callsToolThenStops_completesWithinBudget() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(2);
        assertThat(result.finalMessage()).isEqualTo("DONE");
    }

    @Test
    void agentLoop_neverStops_hitsBudget() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 3, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        assertThat(result.turns()).isEqualTo(3);
    }

    @Test
    void agentLoop_cancellationRequested_stopsCooperatively() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> true, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
    }

    @Test
    void agentLoop_noChatModel_throws() {
        AgentLoopRunner runner = newTestRunner(List.of(), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> runner.run("system", "do it", tools, 5, () -> false, null, null))
                .withMessageContaining("No ChatModel");
    }

    @Test
    void agentLoop_invokesUsageSinkOncePerModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeSandbox(), "fake-session");
        List<ChatResponse> recorded = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, recorded::add, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(recorded).hasSize(2);
    }
}
