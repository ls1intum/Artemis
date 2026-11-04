package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;

import de.tum.cit.aet.artemis.aiworker.api.SandboxUnavailableException;

class AgentLoopRunnerTest {

    @Test
    void toolEnabledEntryPointsRequireSafetyHooks() {
        assertThat(java.util.Arrays.stream(AgentLoopRunner.class.getMethods()).filter(method -> method.getName().equals("run") || method.getName().equals("runSession")))
                .allSatisfy(method -> assertThat(method.getParameterTypes()).contains(SubmitVetoAware.class).doesNotContain(Object.class));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> runner(mock(ChatModel.class)).run("system", "brief", null, 1, () -> false, null, null));
    }

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
    void usageSinksWithoutOptionalHooksRemainCompatibleWithToolSessions() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse edit = calls("edit", "write");
        ChatResponse finished = text("finished");
        when(model.call(any(Prompt.class))).thenReturn(edit, finished);
        List<ChatResponse> responses = new ArrayList<>();
        List<Long> toolCounts = new ArrayList<>();
        AtomicBoolean uncertain = new AtomicBoolean();
        ProviderUsageSink usage = new ProviderUsageSink() {

            @Override
            public void accept(ChatResponse response) {
                responses.add(response);
            }

            @Override
            public void recordToolCalls(long count) {
                toolCounts.add(count);
            }

            @Override
            public void markUncertain() {
                uncertain.set(true);
            }
        };

        usage.recordAttempt();
        var result = runner(model).run("system", "brief", new RecordingTools(), 4, () -> false, usage, null);

        assertThat(result).isEqualTo(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 2, "finished"));
        assertThat(responses).containsExactly(edit, finished);
        assertThat(toolCounts).containsExactly(1L, 0L);
        assertThat(uncertain).isFalse();
    }

    @Test
    void cancellationOutsideASessionDoesNotInventModelTurns() {
        assertThat(AgentLoopResult.outsideSession(AgentLoopResult.Status.CANCELLED, "Cancelled before authoring"))
                .isEqualTo(new AgentLoopResult(AgentLoopResult.Status.CANCELLED, 0, "Cancelled before authoring"));
    }

    @Test
    void mixedSubmitBatchRejectsAllCallsBeforeAnyWorkspaceChange() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(calls("stop", "submit", "edit", "write"), calls("edit", "write", "stop", "submit"), calls("stop", "submit"));
        RecordingTools tools = new RecordingTools();

        var session = runner(model).runSession("system", null, "brief", tools, 4, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(session.result().turns()).isEqualTo(3);
        assertThat(tools.actions).containsExactly("submit");
        var results = (ToolResponseMessage) session.conversation().getLast();
        assertThat(results.getResponses()).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("stop");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.times(3)).call(prompts.capture());
        for (int turn = 1; turn <= 2; turn++) {
            var rejection = (ToolResponseMessage) prompts.getAllValues().get(turn).getInstructions().getLast();
            assertThat(rejection.getResponses()).allSatisfy(result -> assertThat(result.responseData()).contains("no call in this batch was executed"));
        }
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

    @ParameterizedTest
    @CsvSource({ "true", "false" })
    void failedSubmitCannotCompleteBeforeRecovery(boolean throwsException) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(calls("stop", "submit"));
        RecordingTools tools = new RecordingTools();
        tools.failSubmit = true;
        tools.throwSubmit = throwsException;

        var result = runner(model).run("system", "brief", tools, 1, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        assertThat(tools.actions).containsExactly("submit");
        verify(model).call(any(Prompt.class));
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
    void truncatedTextResponseIsNotReportedAsComplete() {
        ChatModel model = mock(ChatModel.class);
        var truncated = new ChatResponse(List.of(new Generation(new AssistantMessage("partial answer"), ChatGenerationMetadata.builder().finishReason("length").build())));
        when(model.call(any(Prompt.class))).thenReturn(truncated);

        var session = runner(model).runTextSession("system", null, "brief", 1, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(model).call(any(Prompt.class));
    }

    /** Runaway text that fills the output limit ended whole generation runs; its call completed, so the loop drops the text and asks once for a short, acting reply. */
    @Test
    void aTextReplyCutOffAtTheLimitIsDiscardedAndTheAgentIsAskedOnceToAct() {
        ChatModel model = mock(ChatModel.class);
        var truncated = new ChatResponse(List.of(new Generation(new AssistantMessage("runaway text"), ChatGenerationMetadata.builder().finishReason("length").build())));
        when(model.call(any(Prompt.class))).thenReturn(truncated, calls("1", "write"), text("finished"));
        RecordingTools tools = new RecordingTools();

        var result = runner(model).run("system", "brief", tools, 6, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(tools.actions).containsExactly("write");
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.times(3)).call(prompts.capture());
        List<Message> secondPrompt = prompts.getAllValues().get(1).getInstructions();
        assertThat(secondPrompt).noneMatch(message -> message instanceof AssistantMessage assistant && "runaway text".equals(assistant.getText()));
        assertThat(secondPrompt.getLast().getText()).contains("cut off at the output token limit", "act through a tool call");
    }

    @Test
    void twoConsecutiveTextRepliesCutOffAtTheLimitEndTheSession() {
        ChatModel model = mock(ChatModel.class);
        var truncated = new ChatResponse(List.of(new Generation(new AssistantMessage("runaway text"), ChatGenerationMetadata.builder().finishReason("length").build())));
        when(model.call(any(Prompt.class))).thenReturn(truncated, truncated, text("finished"));

        var session = runner(model).runSession("system", null, "brief", new RecordingTools(), 6, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(session.result().stopCause()).contains("cut off at the output token limit on turn 2");
        verify(model, org.mockito.Mockito.times(2)).call(any(Prompt.class));
    }

    @Test
    void unavailableToolNameIsNotSentToInstructorProgress() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(calls("bad", "private-instructor-text"), text("finished"));
        List<String> progress = new ArrayList<>();

        var result = runner(model).run("system", "brief", new RecordingTools(), 4, () -> false, null, progress::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(progress).anyMatch(message -> message.contains("unavailable action")).noneMatch(message -> message.contains("private-instructor-text"));
    }

    @Test
    void validCallBeforeUnknownCallDoesNotChangeWorkspace() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(calls("one", "write", "two", "unknown"), text("finished"));
        RecordingTools tools = new RecordingTools();

        var result = runner(model).run("system", "brief", tools, 4, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(tools.actions).isEmpty();
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions().stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream()).toList()).hasSize(2)
                .allSatisfy(response -> assertThat(response.responseData()).contains("no call in this batch was executed"));
    }

    @Test
    void validCallBeforeMalformedArgumentsDoesNotChangeWorkspace() {
        ChatModel model = mock(ChatModel.class);
        var batch = new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("one", "function", "write", "{}"), new AssistantMessage.ToolCall("two", "function", "write", "{"))).build())));
        when(model.call(any(Prompt.class))).thenReturn(batch, text("finished"));
        RecordingTools tools = new RecordingTools();

        var result = runner(model).run("system", "brief", tools, 4, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(tools.actions).isEmpty();
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

    @ParameterizedTest
    @CsvSource({ "terminated,true,CANCELLED", "exception,true,CANCELLED", "submit,true,CANCELLED", "terminated,false,ERROR", "exception,false,ERROR", "submit,false,COMPLETED" })
    void cancellationDuringToolsTakesPrecedenceOverOtherExits(String exit, boolean cancel, AgentLoopResult.Status expected) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(exit.equals("submit") ? calls("submit-id", "submit") : calls("write-id", "write"));
        AtomicBoolean cancelled = new AtomicBoolean();
        var result = runner(model).run("system", "brief", new CancellingTools(cancelled, cancel, exit), 4, cancelled::get, null, null);
        assertThat(result.status()).isEqualTo(expected);
        verify(model).call(any(Prompt.class));
    }

    static class CancellingTools implements SubmitVetoAware {

        private final AtomicBoolean cancelled;

        private final boolean cancel;

        private final String exit;

        CancellingTools(AtomicBoolean cancelled, boolean cancel, String exit) {
            this.cancelled = cancelled;
            this.cancel = cancel;
            this.exit = exit;
        }

        @Tool(description = "Finish an in-flight edit")
        public String write() {
            cancelled.set(cancel);
            if (exit.equals("exception")) {
                throw new SandboxUnavailableException("Sandbox stopped", null);
            }
            return "finished";
        }

        @Tool(description = "Submit the current workspace")
        public String submit() {
            cancelled.set(cancel);
            return "submitted";
        }

        @Override
        public boolean consumeSubmitVeto() {
            return false;
        }

        @Override
        public boolean isSandboxSessionTerminated() {
            return exit.equals("terminated");
        }
    }

    /**
     * A refused command interleaved with successful calls never reaches the consecutive-failure limit, so one session repeated the same refused command dozens of times. The
     * third identical rejection is answered explicitly, and every repeat from then on counts toward ending the session.
     */
    @Test
    void anIdenticalRejectedCallRepeatedBetweenSuccessfulCallsIsEscalatedAndEndsTheSession() {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger turn = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = turn.incrementAndGet();
            String name = current % 2 == 1 ? "shell" : "write";
            String arguments = current % 2 == 1 ? "{\"command\":\"ls -la /root/.gradle\"}" : "{\"path\":\"A" + current + ".java\"}";
            var call = new AssistantMessage.ToolCall("call-" + current, "function", name, arguments);
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        });
        RefusingTools tools = new RefusingTools();

        var session = runner(model).runSession("system", null, "brief", tools, 60, () -> false, null, null);

        int lastAllowedRepeat = AgentToolBatch.REPEATED_REJECTION_ESCALATION - 1 + 5;
        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(tools.refusals).isEqualTo(lastAllowedRepeat);
        assertThat(session.result().turns()).isEqualTo(2 * lastAllowedRepeat - 1);
        List<String> shellResults = session.conversation().stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream()).filter(response -> response.name().equals("shell")).map(ToolResponseMessage.ToolResponse::responseData)
                .toList();
        assertThat(shellResults).hasSize(lastAllowedRepeat);
        assertThat(shellResults.subList(0, 2)).allSatisfy(result -> assertThat(result).contains("the command was not run").doesNotContain("this exact shell call"));
        assertThat(shellResults.get(2)).contains("this exact shell call has now been rejected 3 times", "Repeating it unchanged will not help", "the command was not run");
        assertThat(session.result().stopCause()).startsWith("The agent session stopped: The agent repeated identical rejected tool calls 5 times")
                .contains("shell: ERROR: this exact shell call");
    }

    /**
     * A command whose result never changes cannot make progress: one staged session ran the same {@code git status} 31 times. From the third identical call with an identical
     * result, the result is replaced by a note. The note is not a rejection, so it never ends the session on its own; the turn budget bounds the repeats.
     */
    @Test
    void anIdenticalSuccessfulCallWithAnUnchangedResultIsAnsweredWithANoteButDoesNotEndTheSession() {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger turn = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = turn.incrementAndGet();
            if (current > 12) {
                return text("finished");
            }
            var call = new AssistantMessage.ToolCall("call-" + current, "function", "status", "{}");
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        });

        var session = runner(model).runSession("system", null, "brief", new UnchangingTools(), 60, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        List<String> statusResults = session.conversation().stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream()).filter(response -> response.name().equals("status")).map(ToolResponseMessage.ToolResponse::responseData)
                .toList();
        assertThat(statusResults).hasSize(12);
        assertThat(statusResults.subList(0, 2)).allSatisfy(result -> assertThat(result).contains("not a git repository").doesNotContain("this exact"));
        assertThat(statusResults.subList(2, 12))
                .allSatisfy(result -> assertThat(result).startsWith("NOTE: this exact status call has now returned the same result").contains("not a git repository"));
    }

    /** A model that sends one rejected call several times in a single response must read the escalation before the repeats can end the session. */
    @Test
    void identicalRejectedCallsInOneResponseCountOnceTowardEndingTheSession() {
        ChatModel model = mock(ChatModel.class);
        List<AssistantMessage.ToolCall> repeated = IntStream.range(0, 7)
                .mapToObj(index -> new AssistantMessage.ToolCall("call-" + index, "function", "shell", "{\"command\":\"ls -la /root/.gradle\"}")).toList();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(repeated).build()))),
                text("finished"));

        var session = runner(model).runSession("system", null, "brief", new RefusingTools(), 10, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
    }

    /** A discarded cut-off reply must not come back as the final message, which the specification stage would otherwise write to SPEC.md. */
    @Test
    void aDiscardedCutOffReplyIsNotTheFinalMessage() {
        ChatModel model = mock(ChatModel.class);
        var truncated = new ChatResponse(
                List.of(new Generation(new AssistantMessage("# SPEC.md\n## Rules\n| R1 | cut o"), ChatGenerationMetadata.builder().finishReason("length").build())));
        when(model.call(any(Prompt.class))).thenReturn(truncated, calls("1", "write"));

        var result = runner(model).run("system", "brief", new RecordingTools(), 2, () -> false, null, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        assertThat(result.finalMessage()).doesNotContain("# SPEC.md");
    }

    /** One session rewrote the same file after other edits had changed it; a write's result does not show that, so identical writes must never end the session. */
    @Test
    void identicalSuccessfulWritesAreNotTreatedAsRepeats() {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger turn = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = turn.incrementAndGet();
            if (current > 8) {
                return text("finished");
            }
            var call = new AssistantMessage.ToolCall("call-" + current, "function", "write_file", "{\"path\":\"A.java\"}");
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        });

        var session = runner(model).runSession("system", null, "brief", new FileTools(), 60, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(session.conversation().stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())).noneMatch(response -> response.responseData().contains("this exact"));
    }

    static class FileTools implements SubmitVetoAware {

        @Tool(name = "write_file", description = "Write a file")
        public String writeFile(@org.springframework.ai.tool.annotation.ToolParam(description = "the path") String path) {
            return "Wrote 793 characters to " + path;
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

    @Test
    void anIdenticalCallWhoseResultChangesIsNotARepeat() {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger turn = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = turn.incrementAndGet();
            if (current > 8) {
                return text("finished");
            }
            var call = new AssistantMessage.ToolCall("call-" + current, "function", "read", "{}");
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        });

        var session = runner(model).runSession("system", null, "brief", new UnchangingTools(), 60, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(session.conversation().stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())).noneMatch(response -> response.responseData().contains("this exact"));
    }

    static class UnchangingTools implements SubmitVetoAware {

        int reads;

        @Tool(description = "Show the version control status")
        public String status() {
            return "fatal: not a git repository (or any of the parent directories): .git";
        }

        @Tool(description = "Read a file that changes between reads")
        public String read() {
            return "revision " + ++reads;
        }

        @Tool(description = "Record an edit")
        public String write(@org.springframework.ai.tool.annotation.ToolParam(description = "the path") String path) {
            return "written " + path;
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

    @Test
    void consecutiveRefusedCommandsEndTheSessionWithTheirCause() {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger turn = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = turn.incrementAndGet();
            var call = new AssistantMessage.ToolCall("call-" + current, "function", "shell", "{\"command\":\"ls /root/.gradle/" + current + "\"}");
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        });

        var session = runner(model).runSession("system", null, "brief", new RefusingTools(), 60, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(session.result().turns()).isEqualTo(5);
        assertThat(session.result().stopCause()).isEqualTo("The agent session stopped: 5 consecutive tool calls were rejected. Last rejection: shell: "
                + "\"ERROR: the command was not run. The dependency cache is not inspectable.\"");
    }

    @Test
    void differentRejectedCallsAreNotTreatedAsRepeats() {
        ChatModel model = mock(ChatModel.class);
        AtomicInteger turn = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = turn.incrementAndGet();
            if (current > 8) {
                return text("finished");
            }
            String name = current % 2 == 1 ? "shell" : "write";
            String arguments = current % 2 == 1 ? "{\"command\":\"ls /root/.gradle/" + current + "\"}" : "{\"path\":\"A" + current + ".java\"}";
            var call = new AssistantMessage.ToolCall("call-" + current, "function", name, arguments);
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        });

        var session = runner(model).runSession("system", null, "brief", new RefusingTools(), 60, () -> false, null, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(session.conversation().stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())).noneMatch(response -> response.responseData().contains("this exact"));
    }

    static class RefusingTools implements SubmitVetoAware {

        int refusals;

        @Tool(description = "Run a shell command")
        public String shell(@org.springframework.ai.tool.annotation.ToolParam(description = "the command") String command) {
            refusals++;
            return "ERROR: the command was not run. The dependency cache is not inspectable.";
        }

        @Tool(description = "Record an edit")
        public String write(@org.springframework.ai.tool.annotation.ToolParam(description = "the path") String path) {
            return "written " + path;
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

        boolean failSubmit;

        boolean throwSubmit;

        /** Each recorded edit changes the workspace, so its result differs from the previous one, as a real write's does. */
        @Tool(description = "Record an edit")
        public String write() {
            actions.add("write");
            return "written " + actions.size();
        }

        @Tool(description = "Submit the current workspace")
        public String submit() {
            actions.add("submit");
            if (failSubmit) {
                if (throwSubmit) {
                    throw new IllegalStateException("submission failed before veto assignment");
                }
                return "ERROR: submission failed";
            }
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
