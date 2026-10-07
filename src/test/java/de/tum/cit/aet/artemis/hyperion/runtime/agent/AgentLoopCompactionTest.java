package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/** Deterministic tests for the agent loop's context-window management: no LLM and no Docker. */
class AgentLoopCompactionTest {

    private static AgentLoopRunner newTestRunner(List<ChatModel> chatModels, int contextWindowTokens) {
        return new AgentLoopRunner(chatModels, contextWindowTokens, java.time.Duration.ofMinutes(5), new TestProviderFailureCooldown());
    }

    private static AssistantMessage assistantToolCall(String id, String name, String arguments) {
        return AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments))).build();
    }

    private static ToolResponseMessage toolResult(String id, String name, String data) {
        return ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(id, name, data))).build();
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** Builds [System, User, then {@code turns} pairs of (assistant bash tool-call, large tool result)]. */
    private static List<Message> conversationWithTurns(int turns, int resultChars) {
        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage("system prompt"));
        conversation.add(new UserMessage("create a bubble-sort exercise"));
        for (int i = 0; i < turns; i++) {
            conversation.add(assistantToolCall("call-" + i, "bash", "{\"command\":\"sh verify.sh solution\"}"));
            conversation.add(toolResult("call-" + i, "bash", "x".repeat(resultChars)));
        }
        return conversation;
    }

    @Test
    void continuationCompactsACompletedSessionBeforeBuildingItsFirstPrompt() {
        ChatModel model = mock(ChatModel.class);
        String closingResponse = "detail ".repeat(16_300);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(assistantToolCall("write", "write", "{}")))), textResponse(closingResponse),
                textResponse("The exercise files are ready."), textResponse("continued"));
        List<Message> prior = conversationWithTurns(6, 6_000);
        int window = Math.toIntExact(
                AgentConversationContext.estimateTokens(prior, 0, prior.size()) + AgentConversationContext.estimateMessageTokens(new UserMessage("first stage")) + 20_608);
        AgentLoopRunner runner = newTestRunner(List.of(model), window);
        var tools = new AgentLoopRunnerTest.RecordingTools();
        var completed = runner.runSession("system prompt", prior.subList(1, prior.size()), "first stage", tools, 4, () -> false, null, null);
        assertThat(completed.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(completed.conversation().getLast().getText()).isEqualTo(closingResponse);

        List<Message> nextPrompt = new ArrayList<>();
        nextPrompt.add(new SystemMessage("system prompt"));
        nextPrompt.addAll(completed.conversation());
        nextPrompt.add(new UserMessage("continue"));
        assertThat(window - AgentConversationContext.estimateTokens(nextPrompt, 0, nextPrompt.size()) - 4_096).isLessThan(1_024);
        var resumed = runner.runSession("system prompt", completed.conversation(), "continue", tools, 4, () -> false, null, null);

        assertThat(resumed.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(4)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getInstructions()).anySatisfy(message -> assertThat(message.getText()).contains("SESSION SUMMARY"));
        assertThat(prompts.getAllValues().getLast().getInstructions().getLast().getText()).isEqualTo("continue");
        assertThatNoException().isThrownBy(() -> AgentConversationContext.assertValidPairing(resumed.conversation()));
    }

    @Test
    void cancelledContinuationDoesNotCompactOrCalculatePromptOptions() {
        ChatModel model = mock(ChatModel.class);
        AgentLoopRunner runner = newTestRunner(List.of(model), 21_000);
        List<Message> prior = conversationWithTurns(12, 24_000);

        var result = runner.runTextSession("system", prior.subList(1, prior.size()), "continue", 4, () -> true, null, null);

        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        assertThat(result.result().turns()).isZero();
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void estimateMessageTokens_addsStructuralOverheadOnTopOfJtokkit() {
        // 30 repeated 'x' chars tokenize to 5 tokens under jtokkit's o200k_base encoding, plus the flat per-message overhead (4).
        assertThat(AgentConversationContext.estimateMessageTokens(new UserMessage("x".repeat(30)))).isEqualTo(5 + 4);
        // A tool result: per-message overhead (4) + per-result overhead (8) + the jtokkit count of the payload (5).
        assertThat(AgentConversationContext.estimateMessageTokens(toolResult("c", "bash", "x".repeat(30)))).isEqualTo(4 + 8 + 5);
    }

    @Test
    void estimateContextTokens_anchorsToRealUsageAndAddsOnlyTheDelta() {
        List<Message> conversation = conversationWithTurns(2, 30); // 2 turns appended after a hypothetical earlier call
        // With real usage (5000) reported at conversation size 2, only messages [2..] are estimated and added on top.
        long delta = AgentConversationContext.estimateMessageTokens(conversation.get(2)) + AgentConversationContext.estimateMessageTokens(conversation.get(3))
                + AgentConversationContext.estimateMessageTokens(conversation.get(4)) + AgentConversationContext.estimateMessageTokens(conversation.get(5));
        assertThat(AgentConversationContext.estimateContextTokens(conversation, 5000, 2)).isEqualTo(5000 + delta);
        // Without usage yet (0), the whole conversation is estimated from scratch.
        assertThat(AgentConversationContext.estimateContextTokens(conversation, 0, 2)).isEqualTo(AgentConversationContext.estimateContextTokens(conversation, 0, 0));
    }

    @Test
    void capToolResponses_truncatesOnlyOversizedResultsAndKeepsHeadAndTail() {
        List<Message> conversation = new ArrayList<>();
        conversation.add(assistantToolCall("c1", "bash", "{}"));
        conversation.add(toolResult("c1", "bash", "HEAD" + "m".repeat(40_000) + "TAIL"));
        conversation.add(assistantToolCall("c2", "read_file", "{}"));
        conversation.add(toolResult("c2", "read_file", "short output"));

        AgentConversationContext.capToolResponses(conversation);

        String capped = ((ToolResponseMessage) conversation.get(1)).getResponses().getFirst().responseData();
        assertThat(capped).hasSizeLessThan(40_000).startsWith("HEAD").endsWith("TAIL").contains("characters elided");
        assertThat(((ToolResponseMessage) conversation.get(3)).getResponses().getFirst().responseData()).isEqualTo("short output");
        assertThat(((ToolResponseMessage) conversation.get(1)).getResponses().getFirst().name()).isEqualTo("bash");
    }

    @Test
    void assertValidPairing_acceptsValidAndRejectsOrphans() {
        List<Message> valid = List.of(new SystemMessage("s"), new UserMessage("u"), assistantToolCall("c", "bash", "{}"), toolResult("c", "bash", "ok"));
        assertThatNoException().isThrownBy(() -> AgentConversationContext.assertValidPairing(valid));

        List<Message> orphanResult = List.of(new SystemMessage("s"), new UserMessage("u"), toolResult("c", "bash", "ok"));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> AgentConversationContext.assertValidPairing(orphanResult));

        List<Message> unanswered = List.of(new SystemMessage("s"), new UserMessage("u"), assistantToolCall("c", "bash", "{}"), new UserMessage("nudge"));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> AgentConversationContext.assertValidPairing(unanswered));
    }

    @Test
    void findCutIndex_landsOnATurnStartNeverAToolResult() {
        AgentLoopRunner runner = newTestRunner(List.of(mock(ChatModel.class)), 128_000);
        // 12 turns whose results tokenize to ~3k tokens each comfortably exceed the 20k-token keep-recent target, so a cut is taken.
        List<Message> conversation = conversationWithTurns(12, 24_000);
        int cut = runner.findCutIndex(conversation, 2);
        assertThat(cut).isGreaterThan(2).isLessThan(conversation.size());
        assertThat(conversation.get(cut)).isInstanceOf(AssistantMessage.class);
        assertThat(conversation.get(cut)).isNotInstanceOf(ToolResponseMessage.class);
    }

    @Test
    void findCutIndex_pushesForwardUntilTheKeptTailFitsTheBudget() {
        // A tiny window (24k tokens => ~3.5k budget after the response reserve) forces most turns into the summary so the kept tail fits.
        AgentLoopRunner runner = newTestRunner(List.of(mock(ChatModel.class)), 24_000);
        List<Message> conversation = conversationWithTurns(20, 9_000);
        int cut = runner.findCutIndex(conversation, 2);
        long budget = 24_000L - 20_480L;
        // The kept tail (everything from the cut to the end) must fit under the budget — keepRecent is a target, the real floor is "the tail must fit".
        long tailTokens = 0;
        for (int i = cut; i < conversation.size(); i++) {
            tailTokens += AgentConversationContext.estimateMessageTokens(conversation.get(i));
        }
        assertThat(tailTokens).isLessThanOrEqualTo(budget);
    }

    @Test
    void findCutIndex_whenEvenTheLastTurnDoesNotFit_dropsTheWholeTail() {
        // A window whose budget is below even one turn's tokens: the push-forward loop advances the cut to the end, so the conversation becomes summary-only.
        AgentLoopRunner runner = newTestRunner(List.of(mock(ChatModel.class)), 20_600);
        List<Message> conversation = conversationWithTurns(6, 9_000);
        int cut = runner.findCutIndex(conversation, 2);
        assertThat(cut).isEqualTo(conversation.size());
    }

    @Test
    void compact_summarizesOldTurnsKeepsRecentVerbatimAndStaysPaired() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().reasoningEffort("medium").serviceTier("priority").customHeaders(Map.of("X-Test", "value")).build());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("## Goal\nBuild a bubble-sort exercise.\n## Next steps\nFinish the tests."));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);

        List<Message> conversation = conversationWithTurns(12, 24_000);
        List<Message> compacted = runner.compact(conversation, null);

        ArgumentCaptor<Prompt> summaryPrompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(summaryPrompt.capture());
        assertThat(summaryPrompt.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxCompletionTokens()).isEqualTo(4_096);
            assertThat(options.getMaxTokens()).isNull();
            assertThat(options.getReasoningEffort()).isEqualTo("low");
            assertThat(options.getServiceTier()).isEqualTo("priority");
            assertThat(options.getCustomHeaders()).containsEntry("X-Test", "value");
        });

        assertThat(compacted.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(compacted.get(1)).isInstanceOf(UserMessage.class);
        assertThat(compacted.get(1).getText()).isEqualTo("create a bubble-sort exercise");
        assertThat(compacted.get(2)).isInstanceOf(UserMessage.class);
        assertThat(compacted.get(2).getText()).contains("SESSION SUMMARY").contains("Build a bubble-sort exercise");
        assertThat(compacted).hasSizeLessThan(conversation.size());
        assertThat(compacted.get(3)).isInstanceOf(AssistantMessage.class);
        assertThatNoException().isThrownBy(() -> AgentConversationContext.assertValidPairing(compacted));
    }

    @Test
    void compact_preservesTheLegacyTokenParameter() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().maxTokens(1_234).build());
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("summary"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);

        runner.compact(conversationWithTurns(12, 24_000), null);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getMaxTokens()).isEqualTo(1_234);
            assertThat(options.getMaxCompletionTokens()).isNull();
        });
    }

    @Test
    void compact_onRejectedSummarizer_dropsOldTurnsBehindAMarkerWithoutThrowing() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new ProviderFailureCooldown.ProviderInCooldownException(java.time.Instant.now().plusSeconds(60)));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);

        List<Message> conversation = conversationWithTurns(12, 24_000);
        List<Message> compacted = runner.compact(conversation, null);

        assertThat(compacted.get(2).getText()).contains("SESSION SUMMARY").contains("omitted to fit the context window");
        assertThat(compacted).hasSizeLessThan(conversation.size());
        assertThatNoException().isThrownBy(() -> AgentConversationContext.assertValidPairing(compacted));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uncertainCompactionCannotIssueAnotherTurn(boolean missingResponse) {
        ChatModel chatModel = mock(ChatModel.class);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(chatModel.call(any(Prompt.class))).thenAnswer(ignored -> {
            if (calls.incrementAndGet() == 1) {
                return new ChatResponse(List.of(new Generation(assistantToolCall("write-next", "write", "{}"))));
            }
            if (missingResponse) {
                return null;
            }
            throw com.openai.errors.InternalServerException.builder().statusCode(500).headers(com.openai.core.http.Headers.builder().build()).build();
        });
        ProviderUsageSink usageSink = mock(ProviderUsageSink.class);
        List<Message> prior = new ArrayList<>();
        prior.add(new SystemMessage("system"));
        prior.add(new UserMessage("brief"));
        for (int turn = 0; turn < 12; turn++) {
            prior.add(new AssistantMessage("previous turn"));
            prior.add(new UserMessage("x".repeat(24_000)));
        }
        int contextWindow = Math
                .toIntExact(AgentConversationContext.estimateTokens(prior, 0, prior.size()) + AgentConversationContext.estimateMessageTokens(new UserMessage("continue")) + 20_481);
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), contextWindow);

        assertThatThrownBy(() -> runner.runSession("system", prior.subList(1, prior.size()), "continue", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usageSink, null))
                .hasMessage("Provider usage could not be determined");
        verify(usageSink).markUncertain();
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void compact_onUsageAccountingFailure_stopsInsteadOfContinuingWithAMarker() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(textResponse("summary"));
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        Consumer<ChatResponse> usageSink = ignored -> {
            throw new IllegalStateException("spend guard failed");
        };

        assertThatThrownBy(() -> runner.compact(conversationWithTurns(12, 24_000), usageSink)).hasRootCauseMessage("spend guard failed");
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void compact_whenCancelled_skipsTheSummarizerCall() {
        ChatModel chatModel = mock(ChatModel.class);
        AgentLoopRunner runner = newTestRunner(List.of(chatModel), 128_000);
        List<Message> conversation = conversationWithTurns(12, 24_000);

        assertThat(runner.compact(conversation, null, () -> true)).isSameAs(conversation);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void compact_shortConversation_isReturnedUnchanged() {
        AgentLoopRunner runner = newTestRunner(List.of(mock(ChatModel.class)), 128_000);
        List<Message> conversation = conversationWithTurns(1, 50); // tiny: nothing older than the recent tail to summarize
        assertThat(runner.compact(conversation, null)).isSameAs(conversation);
    }
}
