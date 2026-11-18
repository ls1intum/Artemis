package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

class AgentLoopRunnerBoundaryTest {

    private AgentLoopRunner runner(List<ChatModel> models, int window) {
        return new AgentLoopRunner(models, window, Duration.ZERO, ProviderFailureCooldown.disabled());
    }

    private ChatResponse text(String value) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(value))));
    }

    @Test
    void absentModelFailsWithActionableMessageForEmptyOrNullModelCollections() {
        for (List<ChatModel> models : List.of(List.<ChatModel>of(), Arrays.asList((ChatModel) null))) {
            assertThatThrownBy(() -> runner(models, 128000).runTextSession("System", null, "User", 2, () -> false, null, null)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("No ChatModel is configured. Agentic generation is unavailable.");
        }
    }

    @Test
    void effectiveProfileOptionsReplaceModelDefaultsAndPreserveConfiguredResponseLimit() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("deployment").maxCompletionTokens(4096).build());
        when(model.call(any(Prompt.class))).thenReturn(text("Completed"));
        AgentLoopRunner deployment = runner(List.of(model), 128000);
        assertThat(deployment.forSettings(null)).isSameAs(deployment);
        var profile = new HyperionGenerationSettings("small", "Small", 2, Duration.ofMinutes(5), 1000, false, "FRESH", 64000,
                OpenAiChatOptions.builder().model("profile").maxCompletionTokens(2048).build(), false, true);
        var result = deployment.forSettings(profile).runTextSession("System", List.of(new UserMessage("Earlier")), "Continue", 2, () -> false, null, null);
        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(result.conversation()).extracting(Message::getText).containsExactly("Earlier", "Continue", "Completed");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("profile");
        assertThat(options.getMaxCompletionTokens()).isEqualTo(2048);
        assertThat(options.getMaxTokens()).isNull();
    }

    @Test
    void genericLegacyOptionsAndProfileWithoutOptionsKeepModelSettings() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().model("legacy").maxTokens(3000).build());
        when(model.call(any(Prompt.class))).thenReturn(text("Done"));
        var profile = new HyperionGenerationSettings("default", null, 2, Duration.ofMinutes(5), 1000, false, "FRESH", 64000, null, true, false);
        var result = runner(List.of(model), 128000).forSettings(profile).runTextSession("System", null, "User", 2, () -> false, null, ignored -> {
        });
        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("legacy");
        assertThat(options.getMaxTokens()).isEqualTo(3000);
        assertThat(options.getMaxCompletionTokens()).isNull();
    }

    @Test
    void undersizedContextFailsBeforeProviderCall() {
        ChatModel model = mock(ChatModel.class);
        assertThatThrownBy(() -> runner(List.of(model), 500).runTextSession("System", null, "User", 1, () -> false, null, null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The agent prompt leaves insufficient context for a model response.");
    }

    @Test
    void nullProviderResultMarksUsageUncertainAndStopsWithoutRetrying() {
        ChatModel model = mock(ChatModel.class);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        List<String> progress = new ArrayList<>();
        var result = runner(List.of(model), 128000).runTextSession("System", null, "User", 3, () -> false, usage, progress::add);
        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(result.result().turns()).isEqualTo(1);
        verify(usage).markUncertain();
        verify(model).call(any(Prompt.class));
        assertThat(progress).contains("Thinking about the next step.", "The AI service could not complete the request.");
    }

    @Test
    void accountingFailureNeverReplaysCompletedRequest() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(text("Already charged"));
        AtomicInteger charged = new AtomicInteger();
        var result = runner(List.of(model), 128000).runTextSession("System", null, "User", 3, () -> false, response -> {
            charged.incrementAndGet();
            throw new IllegalStateException("usage store unavailable");
        }, null);
        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(charged).hasValue(1);
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test
    void zeroTurnBudgetReturnsBoundedSessionWithoutCallingModel() {
        ChatModel model = mock(ChatModel.class);
        var result = runner(List.of(model), 128000).runTextSession("System", null, "User", 0, () -> false, null, null);
        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.BUDGET_EXHAUSTED);
        assertThat(result.result().turns()).isZero();
        assertThat(result.conversation()).extracting(Message::getText).containsExactly("User");
    }

    @Test
    void compactionPreservesProtectedMessagesWhenNothingOlderCanBeSummarizedOrCancellationArrives() {
        ChatModel model = mock(ChatModel.class);
        AgentLoopRunner runner = runner(List.of(model), 128000);
        List<Message> brief = List.of(new SystemMessage("System"), new UserMessage("User"), new AssistantMessage("Latest"));
        assertThat(runner.compact(brief, null)).isSameAs(brief);
        List<Message> shortHistory = List.of(new SystemMessage("System"), new UserMessage("User"), new AssistantMessage("Earlier"), new AssistantMessage("Latest"));
        assertThat(runner.compact(shortHistory, null)).isSameAs(shortHistory);
        List<Message> longHistory = new ArrayList<>(shortHistory);
        longHistory.add(new UserMessage("old ".repeat(30000)));
        longHistory.add(new AssistantMessage("recent ".repeat(30000)));
        assertThat(runner.compact(longHistory, null, () -> true)).isSameAs(longHistory);
    }
}
