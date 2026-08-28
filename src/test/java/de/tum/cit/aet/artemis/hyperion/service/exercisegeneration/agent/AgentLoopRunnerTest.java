package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.openai.errors.OpenAIIoException;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class AgentLoopRunnerTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

    private static AgentLoopRunner newTestRunner(List<ChatModel> chatModels, int contextWindowTokens) {
        return newTestRunner(chatModels, contextWindowTokens, Duration.ofMinutes(5), new TestProviderFailureCooldown());
    }

    private static AgentLoopRunner newTestRunner(List<ChatModel> chatModels, int contextWindowTokens, Duration cooldown) {
        return newTestRunner(chatModels, contextWindowTokens, cooldown, new TestProviderFailureCooldown());
    }

    private static AgentLoopRunner newTestRunner(List<ChatModel> chatModels, int contextWindowTokens, Duration cooldown, ProviderFailureCooldown providerFailureCooldown) {
        return new AgentLoopRunner(chatModels, contextWindowTokens, cooldown, providerFailureCooldown);
    }

    private static ChatResponse toolCallResponse(String name, String arguments) {
        var toolCall = new AssistantMessage.ToolCall("call-1", "function", name, arguments);
        var message = AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse lengthTruncatedToolCallResponse(String name, String arguments) {
        var toolCall = new AssistantMessage.ToolCall("call-1", "function", name, arguments);
        var message = AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
        return new ChatResponse(List.of(new Generation(message, org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("length").build())));
    }

    @Test
    void agentLoop_lengthTruncatedToolCalls_areNotExecutedAndTheModelIsAskedToReissue() {
        ChatModel chatModel = mock(ChatModel.class);
        // The truncated turn's write_file content may have been cut off mid-file, so it must never reach the sandbox.
        when(chatModel.call(any(Prompt.class))).thenReturn(lengthTruncatedToolCallResponse("write_file", "{\"path\":\"solution/A.java\",\"content\":\"class A {\"}"),
                textResponse("DONE"));
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(sandbox, "fake-session"), 10, () -> false, usageSink, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(sandbox.executedCommands()).isEmpty();
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        Message toolFeedback = prompts.getAllValues().get(1).getInstructions().stream().filter(ToolResponseMessage.class::isInstance).reduce((first, second) -> second)
                .orElseThrow();
        assertThat(((ToolResponseMessage) toolFeedback).getResponses().getFirst().responseData()).contains("output token limit").contains("Re-issue the call");
        verify(usageSink).recordToolCalls(1);
    }

    @Test
    void agentLoop_pushesOneRecordedTurnToTheSinkForEveryTurnThatBegan() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), toolCallResponse("bash", "{\"command\":\"ls\"}"),
                textResponse("DONE"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);

        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, usageSink, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(usageSink, times(3)).recordTurn();
        assertThat(result.turns()).isEqualTo(3);
    }

    @Test
    void agentLoop_cancelledAtATurnBoundary_leavesTheSinkCountAndTheLoopResultCountDeliberatelyDifferent() {
        // The two counters answer different questions and must not be "fixed" into agreement: AgentLoopResult.turns is per-session control state, while the sink is the run-level
        // total an administrator reads. Reconciling them is what once made gate-abandoned runs invisible.
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), toolCallResponse("bash", "{\"command\":\"ls\"}"),
                textResponse("DONE"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);

        AgentLoopResult firstSession = runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, usageSink, null);
        AgentLoopResult cancelledSession = runner.run("system", "again", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> true, usageSink, null);

        assertThat(firstSession.turns()).isEqualTo(3);
        assertThat(cancelledSession.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        // No turn began in the cancelled session, yet the sink still carries the whole run's three turns.
        assertThat(cancelledSession.turns()).isZero();
        verify(usageSink, times(3)).recordTurn();
    }

    @Test
    void agentLoop_withoutAProviderUsageSink_recordsTurnsWithoutFailing() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        List<ChatResponse> plainSink = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, plainSink::add, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(plainSink).hasSize(1);
    }

    @Test
    void agentLoop_recoversFromUnknownToolCall_andContinues() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("apply_patch", "{\"path\":\"x\"}"), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, usageSink, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        verify(usageSink).recordToolCalls(1);
    }

    @Test
    void agentLoop_recoverableFileToolRejectionsDoNotTerminateTheJob() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse rejectedSearch = toolCallResponse("search", "{\"path\":\"solution/A.java\",\"query\":\"}\\n}\"}");
        when(chatModel.call(any(Prompt.class))).thenReturn(rejectedSearch, rejectedSearch, rejectedSearch, rejectedSearch, rejectedSearch, rejectedSearch, textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(7)).call(prompts.capture());
        String recoveryPrompt = prompts.getAllValues().get(2).getInstructions().stream().map(Message::getText).collect(Collectors.joining("\n"));
        assertThat(recoveryPrompt).contains("Two file-tool actions were rejected", "rewrite the complete small file with write_file", "Search accepts only one-line text");
    }

    @Test
    void agentLoopFailsImmediatelyWhenATimedOutCommandTerminatesTheSandbox() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"sleep 999\"}"), textResponse("must not be called"));
        // Every command reports the timed-out sandbox, so the loop must give up rather than keep issuing turns against a dead session.
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(new SandboxExecResultDTO(-1, "", "", true));
        List<String> steps = new ArrayList<>();

        Object tools = new FileChangeEmittingAgentTools(new SandboxAgentTools(sandbox, "fake-session"), ignored -> {
        });
        AgentLoopResult result = newTestRunner(List.of(chatModel), 128_000).run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(steps).contains("The build environment stopped responding.");
    }

    @Test
    void agentLoopFailsImmediatelyWhenAToolsImplementationOtherThanTheTwoKnownOnesReportsATerminatedSandbox() {
        // The loop must learn "the sandbox is gone" from the SubmitVetoAware contract: a type switch over the two known tools classes answers "sandbox alive" for any third
        // implementation and keeps calling a dead sandbox for the rest of the turn budget.
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("must not be called"));
        DeadSandboxTools tools = new DeadSandboxTools();
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = newTestRunner(List.of(chatModel), 128_000).run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(tools.executedCommands).containsExactly("ls"); // the turn's tools did run; what must not happen is a second turn against the dead session
        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(steps).contains("The build environment stopped responding.");
    }

    /** A tools object that is neither {@link SandboxAgentTools} nor {@link FileChangeEmittingAgentTools} — the third implementation a type switch cannot see. */
    private static final class DeadSandboxTools implements SubmitVetoAware {

        private final List<String> executedCommands = new ArrayList<>();

        @Tool(name = "bash", description = "Runs a command in the workspace.")
        String bash(@ToolParam(description = "The shell command to run.") String command) {
            executedCommands.add(command);
            return "";
        }

        @Override
        public boolean consumeSubmitVeto() {
            return false;
        }

        @Override
        public boolean isSandboxSessionTerminated() {
            return true;
        }
    }

    @Test
    void agentLoop_scrubsHarmonyTokensFromModelResponses() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE<|end|>"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.finalMessage()).isEqualTo("DONE");
    }

    @Test
    void agentLoop_doesNotMultiplyTheSdkRetryLadder() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new OpenAIIoException("read timed out")).thenReturn(textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, usageSink, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class));
        // Once the provider call starts, a transport failure cannot prove that the request was never accepted, retried, or billed.
        verify(usageSink).markUncertain();
        verify(usageSink, never()).accept(any());
        assertThat(steps).contains("The AI service could not complete the request.").noneMatch(step -> step.contains("read timed out"));
    }

    @Test
    void agentLoop_quotaExhaustionFailsFastAndActivatesProviderFailureCooldown() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 429 insufficient_quota: exceeded your current quota"));
        TestProviderFailureCooldown cooldown = new TestProviderFailureCooldown();
        ProviderUsageSink firstUsageSink = mock(ProviderUsageSink.class);

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000, Duration.ofMinutes(5), cooldown);
        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, firstUsageSink, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class));
        verify(firstUsageSink).markUncertain();

        ChatModel secondChatModel = mock(ChatModel.class);
        ProviderUsageSink secondUsageSink = mock(ProviderUsageSink.class);
        AgentLoopRunner secondRunner = newTestRunner(List.of(secondChatModel), 128_000, Duration.ofMinutes(5), cooldown);
        AgentLoopResult secondResult = secondRunner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, secondUsageSink,
                null);

        assertThat(secondResult.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(secondChatModel, times(0)).call(any(Prompt.class));
        // The cooldown rejected the turn locally before the provider call started, so no spend or uncertainty may be attributed to it.
        verify(secondUsageSink, never()).markUncertain();
        verify(secondUsageSink, never()).accept(any());
        verify(secondUsageSink, never()).recordToolCalls(anyLong());
    }

    @Test
    void agentLoop_emptyResponse_isReSampled() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse(""), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setEmptyResponseRetryTimingForTests(0L, 0L);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
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
        runner.setEmptyResponseRetryTimingForTests(0L, 0L);
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(2)).call(any(Prompt.class));
        assertThat(steps).contains("The AI service returned no usable response.");
    }

    @Test
    void agentLoop_interruptedDuringEmptyResponseBackoff_reportsError() {
        ChatModel chatModel = mock(ChatModel.class);
        // An interrupt during the re-sample backoff must surface as ERROR, not be papered over by handing the benign empty response to the loop as a COMPLETED.
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return textResponse("");
        });

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        runner.setEmptyResponseRetryTimingForTests(50L, 50L); // non-zero so the backoff actually sleeps and observes the interrupt
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(chatModel, times(1)).call(any(Prompt.class));
        // The backoff restored the interrupt flag; asserting through Thread.interrupted() also clears it so it cannot leak into other tests.
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void agentLoop_normalizesLeakedHarmonyToolName_andDispatchesToTheRealTool() {
        ChatModel chatModel = mock(ChatModel.class);
        // A model server can leak a harmony control token into the tool name; unnormalized it matches no registered tool and the loop thrashes on tool-execution failures.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash<|channel|>commentary", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
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
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "fake-session");
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(1);
        assertThat(steps).filteredOn("Submitting the exercise for verification."::equals).hasSize(1);
        assertThat(sandbox.executedCommands()).as("the co-requested bash command was executed before the loop ended").anyMatch(command -> command.contains("ls"));
    }

    @Test
    void agentLoop_submitVetoedByAStagedStageCheck_continuesTheLoopUntilAFixedResubmitEndsIt() {
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), any(), anyString(), eq(exercise), eq(Map.of()), any(), any(SeededStructuralTests.class)))
                .thenReturn(StageCheckResult.failed("the reference solution does not compile"), StageCheckResult.passed(""));
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session", null, exercise, Map.of(), false, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("submit", "{}"), toolCallResponse("write_file", "{\"path\":\"solution/A.java\",\"content\":\"fixed\"}"),
                toolCallResponse("submit", "{}"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        List<String> steps = new ArrayList<>();

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(3);
        assertThat(steps).contains("Submit was rejected by the stage check; continuing to address the reported issues.");
        verify(chatModel, times(3)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_nonStagedSubmit_isNeverVetoed_endsOnTheFirstCallEvenWhenTheToolsObjectImplementsSubmitVetoAware() {
        // SandboxAgentTools always implements SubmitVetoAware, so implementing the interface must not by itself gate a session that was never staged.
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("submit", "{}"), textResponse("must not be called"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"); // currentStage stays null: unstaged/legacy

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.turns()).isEqualTo(1);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_appendsTheBudgetPressureNudge_intoThePromptOfTheFinalAllowedTurn() {
        ChatModel chatModel = mock(ChatModel.class);
        // The nudge must be appended after the conversation is rebuilt from turn 1's tool result, or the rebuild discards it and it never reaches the model on turn 2.
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

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
        runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 1, () -> false, null, null);

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
        runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 1, () -> false, null, null);

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
        runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 1, () -> false, null, null);

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
        runner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 1, () -> false, null, null);

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
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
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
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
        List<String> steps = new ArrayList<>();

        runner.run("system", "do it", tools, 10, () -> false, null, steps::add);

        assertThat(steps).contains("Working on a\\b\"c.java.");
    }

    @Test
    void agentLoop_repeatedToolFailures_endWithError() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("apply_patch", "{}"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 20, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
    }

    @Test
    void agentLoop_repeatedRejectedToolActions_endWithError() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse rejectedWrite = toolCallResponse("write_file", "{\"path\":\"verify.sh\",\"content\":\"replacement\"}");
        when(chatModel.call(any(Prompt.class))).thenReturn(rejectedWrite);

        AgentLoopResult result = newTestRunner(List.of(chatModel), 128_000).run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 20,
                () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.turns()).isEqualTo(5);
        verify(chatModel, times(5)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_successfulToolAction_resetsRejectedActionCounter() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse rejectedWrite = toolCallResponse("write_file", "{\"path\":\"verify.sh\",\"content\":\"replacement\"}");
        ChatResponse successfulBash = toolCallResponse("bash", "{\"command\":\"ls\"}");
        when(chatModel.call(any(Prompt.class))).thenReturn(rejectedWrite, rejectedWrite, rejectedWrite, rejectedWrite, successfulBash, rejectedWrite, rejectedWrite, rejectedWrite,
                rejectedWrite, textResponse("DONE"));

        AgentLoopResult result = newTestRunner(List.of(chatModel), 128_000).run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 20,
                () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        verify(chatModel, times(10)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_failedVerifyDiagnostic_resetsRejectedActionCounter() {
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), any(), anyString(), eq(exercise), eq(Map.of()), any(), any(SeededStructuralTests.class)))
                .thenReturn(StageCheckResult.failed("the reference solution does not compile"));
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session", null, exercise, Map.of(), false, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse rejectedWrite = toolCallResponse("write_file", "{\"path\":\"verify.sh\",\"content\":\"replacement\"}");
        ChatResponse failedVerify = toolCallResponse("verify", "{}");
        when(chatModel.call(any(Prompt.class))).thenReturn(rejectedWrite, rejectedWrite, rejectedWrite, rejectedWrite, failedVerify, rejectedWrite, rejectedWrite, rejectedWrite,
                rejectedWrite, textResponse("DONE"));

        AgentLoopResult result = newTestRunner(List.of(chatModel), 128_000).run("system", "do it", tools, 20, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.finalMessage()).isEqualTo("DONE");
        verify(chatModel, times(10)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_callsToolThenStops_completesWithinBudget() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("DONE"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

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
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 3, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        assertThat(result.turns()).isEqualTo(3);
    }

    @Test
    void agentLoop_cancellationRequested_stopsCooperatively() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"));

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

        AgentLoopResult result = runner.run("system", "do it", tools, 10, () -> true, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
    }

    @Test
    void agentLoop_noChatModel_throws() {
        AgentLoopRunner runner = newTestRunner(List.of(), 128_000);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> runner.run("system", "do it", tools, 5, () -> false, null, null))
                .withMessageContaining("No ChatModel");
    }

    @Test
    void agentLoop_rejectsSupportedSecretMaterialBeforeInitialProviderCall() {
        ChatModel chatModel = mock(ChatModel.class);
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);

        assertThatExceptionOfType(de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy.SecretMaterialException.class).isThrownBy(
                () -> runner.run("system", "instruction " + GITHUB_SENTINEL, new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 2, () -> false, null, null))
                .withMessageContaining("GITHUB_TOKEN").withMessageNotContaining(GITHUB_SENTINEL);
        verify(chatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void agentLoop_rejectsSupportedSecretMaterialInCarriedToolCallArguments() {
        ChatModel chatModel = mock(ChatModel.class);
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        AssistantMessage priorAssistant = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "write_file", "{\"content\":\"" + GITHUB_SENTINEL + "\"}"))).build();

        assertThatExceptionOfType(de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy.SecretMaterialException.class)
                .isThrownBy(() -> runner.runTextSession("system", List.of(priorAssistant), "continue", 1, () -> false, null, null)).withMessageContaining("GITHUB_TOKEN")
                .withMessageNotContaining(GITHUB_SENTINEL);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void agentLoop_rejectsSupportedSecretMaterialInNewToolCallArgumentsBeforeExecution() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("write_file", "{\"content\":\"" + GITHUB_SENTINEL + "\"}"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();

        assertThatExceptionOfType(de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy.SecretMaterialException.class)
                .isThrownBy(() -> runner.run("system", "continue", new SandboxAgentTools(sandbox, "fake-session"), 1, () -> false, null, null))
                .withMessageContaining("GITHUB_TOKEN").withMessageNotContaining(GITHUB_SENTINEL);
        assertThat(sandbox.files()).isEmpty();
    }

    @Test
    void agentLoop_recordingModelNeverReceivesSupportedSentinelFromToolObservation() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("read_file", "{\"path\":\"solution/src/fixture.txt\"}"), textResponse("DONE"));
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/src/fixture.txt", GITHUB_SENTINEL);

        AgentLoopResult result = newTestRunner(List.of(chatModel), 128_000).run("system", "inspect the fixture", new SandboxAgentTools(sandbox, "fake-session"), 2, () -> false,
                null, null);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues()).allSatisfy(prompt -> assertThat(renderPrompt(prompt)).doesNotContain(GITHUB_SENTINEL));
        assertThat(renderPrompt(promptCaptor.getAllValues().get(1))).contains("GITHUB_TOKEN");
        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
    }

    // --- runSession: carrying one logical conversation across several bounded run calls (staged generation continuity) ---

    @Test
    void runTextSession_completesWithoutRegisteringToolMethods() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("THREE CONCEPTS"));

        AgentLoopRunner.AgentLoopSession session = newTestRunner(List.of(chatModel), 128_000).runTextSession("system", null, "explore", 1, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(session.result().finalMessage()).isEqualTo("THREE CONCEPTS");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> assertThat(options.getToolCallbacks()).isEmpty());
    }

    @Test
    void runSession_nullPriorConversation_producesTheSameResultAsRun() {
        ChatModel chatModelForRun = mock(ChatModel.class);
        when(chatModelForRun.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("DONE"));
        AgentLoopResult viaRun = newTestRunner(List.of(chatModelForRun), 128_000).run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10,
                () -> false, null, null);

        ChatModel chatModelForSession = mock(ChatModel.class);
        when(chatModelForSession.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), textResponse("DONE"));
        AgentLoopRunner.AgentLoopSession viaSession = newTestRunner(List.of(chatModelForSession), 128_000).runSession("system", null, "do it",
                new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 10, () -> false, null, null);

        assertThat(viaSession.result()).as("a null prior conversation must behave exactly like run()").isEqualTo(viaRun);
        // The returned conversation excludes the system message but carries the rest, ready to seed a later runSession call.
        assertThat(viaSession.conversation()).hasSize(4);
        assertThat(viaSession.conversation().get(0)).isInstanceOfSatisfying(UserMessage.class, message -> assertThat(message.getText()).isEqualTo("do it"));
        assertThat(viaSession.conversation().getLast()).isInstanceOfSatisfying(AssistantMessage.class, message -> assertThat(message.getText()).isEqualTo("DONE"));
    }

    @Test
    void runSession_continuesTheConversation_secondCallSeesTheFirstCallsAssistantTurn() {
        ChatModel firstChatModel = mock(ChatModel.class);
        when(firstChatModel.call(any(Prompt.class))).thenReturn(textResponse("MARKER_FROM_CALL_ONE"));
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

        AgentLoopRunner.AgentLoopSession firstSession = newTestRunner(List.of(firstChatModel), 128_000).runSession("system stage 1", null, "do stage 1", tools, 10, () -> false,
                null, null);
        assertThat(firstSession.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(firstSession.result().finalMessage()).isEqualTo("MARKER_FROM_CALL_ONE");

        ChatModel secondChatModel = mock(ChatModel.class);
        when(secondChatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));

        AgentLoopRunner.AgentLoopSession secondSession = newTestRunner(List.of(secondChatModel), 128_000).runSession("system stage 2", firstSession.conversation(), "do stage 2",
                tools, 10, () -> false, null, null);

        assertThat(secondSession.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(secondChatModel).call(promptCaptor.capture());
        assertThat(renderPrompt(promptCaptor.getValue())).contains("MARKER_FROM_CALL_ONE").contains("system stage 2").contains("do stage 2");
    }

    @Test
    void runSession_carriedConversationOverTheCompactionThreshold_getsCompactedAndKeepsTheSummarySentinel() {
        List<Message> priorConversation = new ArrayList<>();
        priorConversation.add(new UserMessage("create a bubble-sort exercise"));
        for (int i = 0; i < 8; i++) {
            priorConversation.add(AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + i, "function", "bash", "{\"command\":\"sh verify.sh solution\"}"))).build());
            priorConversation.add(ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse("call-" + i, "bash", "x".repeat(8_000)))).build());
        }
        long priorConversationTokens = priorConversation.stream().mapToLong(AgentConversationContext::estimateMessageTokens).sum();
        // Sized so the very first (pre-compaction) call still fits (needs at least ~5_120 tokens of headroom below the window) while the post-turn compaction check fires
        // (triggers once estimated usage exceeds window - 20_480): 12_800 headroom sits squarely between those two thresholds.
        int contextWindowTokens = (int) (priorConversationTokens + 12_800);

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(toolCallResponse("bash", "{\"command\":\"ls\"}"), // turn 1 of the main loop
                textResponse("## Goal\nFinish the bubble-sort exercise.\n## Next steps\nWrite the tests.")); // the out-of-band compaction/summarization call

        AgentLoopRunner runner = newTestRunner(List.of(chatModel), contextWindowTokens);
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session");

        AgentLoopRunner.AgentLoopSession session = runner.runSession("system", priorConversation, "continue", tools, 1, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        verify(chatModel, times(2)).call(any(Prompt.class)); // the main-loop turn, plus exactly one summarization call proves compaction actually ran
        AgentConversationContext.assertValidPairing(session.conversation()); // the compacted conversation still satisfies the tool-pairing contract
        assertThat(session.conversation())
                .anyMatch(message -> message.getText() != null && message.getText().contains("SESSION SUMMARY") && message.getText().contains("Finish the bubble-sort exercise"));
    }

    private static String renderPrompt(Prompt prompt) {
        StringBuilder rendered = new StringBuilder();
        prompt.getInstructions().forEach(message -> {
            if (message instanceof ToolResponseMessage toolResponse) {
                toolResponse.getResponses().forEach(response -> rendered.append(response.responseData()).append('\n'));
            }
            else {
                rendered.append(message.getText()).append('\n');
            }
        });
        return rendered.toString();
    }

    @Test
    void forSettings_pinsTheProfilesModelAndDecodingParametersOnEveryCall() {
        // What reaches the provider must be the named profile's configuration, not the deployment model bean's.
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("deployment-model").temperature(1.0).maxCompletionTokens(8_192).build());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));
        HyperionGenerationSettings settings = new HyperionGenerationSettings("draft", "Quick draft", 20, Duration.ofMinutes(12), 600_000L, true, "CONTINUOUS", 64_000,
                OpenAiChatOptions.builder().model("draft-model").temperature(0.2).maxCompletionTokens(4_096).build(), false, true);

        AgentLoopRunner profileRunner = newTestRunner(List.of(chatModel), 128_000).forSettings(settings);
        profileRunner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 5, () -> false, mock(ProviderUsageSink.class), null);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompts.capture());
        assertThat(prompts.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
        OpenAiChatOptions sent = (OpenAiChatOptions) prompts.getValue().getOptions();
        assertThat(sent.getModel()).isEqualTo("draft-model");
        assertThat(sent.getTemperature()).isEqualTo(0.2);
        // The per-turn completion cap is derived from the profile's own limit, not the deployment bean's 8192.
        assertThat(sent.getMaxCompletionTokens()).isEqualTo(4_096);
    }

    @Test
    void forSettings_sizesThePerTurnOutputBudgetFromTheProfilesContextWindow() {
        // A profile that pins a model also pins that model's window; sending the deployment window's output allowance to a smaller-window model overflows it provider-side.
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("deployment-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));
        HyperionGenerationSettings smallWindow = new HyperionGenerationSettings("draft", "Quick draft", 20, Duration.ofMinutes(12), 600_000L, true, "CONTINUOUS", 8_000, null,
                false, false);

        AgentLoopRunner profileRunner = newTestRunner(List.of(chatModel), 128_000).forSettings(smallWindow);
        profileRunner.run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 5, () -> false, mock(ProviderUsageSink.class), null);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompts.capture());
        Integer profileOutputTokens = ((OpenAiChatOptions) prompts.getValue().getOptions()).getMaxCompletionTokens();
        assertThat(profileOutputTokens).isLessThan(8_000);

        ChatModel deploymentModel = mock(ChatModel.class);
        when(deploymentModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("deployment-model").build());
        when(deploymentModel.call(any(Prompt.class))).thenReturn(textResponse("DONE"));
        newTestRunner(List.of(deploymentModel), 128_000).run("system", "do it", new SandboxAgentTools(new FakeInteractiveSandbox(), "fake-session"), 5, () -> false,
                mock(ProviderUsageSink.class), null);
        ArgumentCaptor<Prompt> deploymentPrompts = ArgumentCaptor.forClass(Prompt.class);
        verify(deploymentModel).call(deploymentPrompts.capture());
        assertThat(((OpenAiChatOptions) deploymentPrompts.getValue().getOptions()).getMaxCompletionTokens()).isGreaterThan(profileOutputTokens);
    }

    @Test
    void forSettings_onlyNullReusesTheSharedRunner() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("deployment-model").build());
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);

        HyperionGenerationSettings deploymentDefault = new HyperionGenerationSettings("", null, 60, Duration.ofMinutes(45), 3_000_000L, true, "CONTINUOUS", 128_000, null, true,
                false);

        assertThat(runner.forSettings(deploymentDefault)).isNotSameAs(runner);
        assertThat(runner.forSettings(null)).isSameAs(runner);
    }
}
