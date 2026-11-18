package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/** Context admission tests use provider counts and deterministic responses, not live model calls. */
class AgentLoopContextAdmissionTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void outputAllowanceUsesProviderAnchorAndNewMessagesWithoutChangingOptionPrecedence(boolean legacy) {
        ChatModel model = mock(ChatModel.class);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model("configured-model").maxTokens(legacy ? 32_768 : 40_000).maxCompletionTokens(legacy ? null : 32_768)
                .temperature(0.6).reasoningEffort("medium").serviceTier("priority").timeout(Duration.ofMinutes(2)).customHeaders(Map.of("X-Test", "value")).build();
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("deployment-model").maxCompletionTokens(2_048).build());
        var firstMessageCount = new AtomicInteger();
        var callCount = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            if (callCount.getAndIncrement() == 0) {
                firstMessageCount.set(prompt.getInstructions().size());
                return toolResponse(179_000);
            }
            return textResponse("done", 179_100);
        });
        var runner = runner(model, 128_000)
                .forSettings(new HyperionGenerationSettings("profile", null, 2, Duration.ofMinutes(5), 300_000, false, "fresh", 200_000, options, false, true));
        List<Message> prior = List.of(new UserMessage("data ".repeat(150_000)));
        var result = runner.runSession("system", prior, "continue", new AgentLoopRunnerTest.RecordingTools(), 2, () -> false, null, null);

        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        Prompt first = prompts.getAllValues().getFirst();
        Prompt next = prompts.getAllValues().getLast();
        long localFirst = AgentConversationContext.estimateTokens(next.getInstructions(), 0, firstMessageCount.get());
        assertThat(localFirst).isBetween(149_000L, 151_000L);
        long delta = AgentConversationContext.estimateTokens(next.getInstructions(), firstMessageCount.get(), next.getInstructions().size());
        int headroom = Math.toIntExact(200_000 - 179_000 - delta - 4_096);
        assertThat(headroom).isLessThan(32_768);
        OpenAiChatOptions actual = (OpenAiChatOptions) next.getOptions();
        if (legacy) {
            assertThat(actual.getMaxTokens()).isEqualTo(headroom);
            assertThat(actual.getMaxCompletionTokens()).isNull();
        }
        else {
            assertThat(((OpenAiChatOptions) first.getOptions()).getMaxCompletionTokens()).isEqualTo(32_768);
            assertThat(actual.getMaxCompletionTokens()).isEqualTo(headroom);
            assertThat(actual.getMaxTokens()).isNull();
        }
        assertThat(actual.getModel()).isEqualTo("configured-model");
        assertThat(actual.getTemperature()).isEqualTo(0.6);
        assertThat(actual.getReasoningEffort()).isEqualTo("medium");
        assertThat(actual.getServiceTier()).isEqualTo("priority");
        assertThat(actual.getTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(actual.getCustomHeaders()).containsEntry("X-Test", "value");
    }

    @Test
    void firstCallIncludesAvailableToolDefinitionOverhead() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().maxCompletionTokens(32_768).build());
        when(model.call(any(Prompt.class))).thenReturn(textResponse("done", 100));
        var tools = new SchemaTools();
        var callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
        long definitions = AgentConversationContext.estimateToolDefinitionTokens(callbacks);
        assertThat(definitions).isGreaterThan(50);
        List<Message> messages = List.of(new SystemMessage("system"), new UserMessage("brief"));
        int window = Math.toIntExact(AgentConversationContext.estimateTokens(messages, 0, messages.size()) + definitions + 4_096 + 1_500);

        var result = runner(model, window).runSession("system", null, "brief", tools, 1, () -> false, null, null);

        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(((OpenAiChatOptions) prompt.getValue().getOptions()).getMaxCompletionTokens()).isEqualTo(1_500);
    }

    @Test
    void compactionInvalidatesTheOldProviderAnchorEvenWhenTheRebuiltHistoryIsNotShorterThanTheAnchoredPrefix() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().maxCompletionTokens(32_768).build());
        when(model.call(any(Prompt.class))).thenReturn(toolResponse(190_000), textResponse("summary", 30_000), textResponse("done", 100));
        var runner = runner(model, 200_000);
        List<Message> prior = List.of(new UserMessage("initial brief"), new UserMessage("old context ".repeat(20_000)));

        var result = runner.runSession("system", prior, "continue", new AgentLoopRunnerTest.RecordingTools(), 2, () -> false, null, null);

        assertThat(result.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(prompts.capture());
        Prompt next = prompts.getAllValues().getLast();
        assertThat(next.getInstructions().size()).isGreaterThanOrEqualTo(4);
        assertThat(next.getInstructions()).anySatisfy(message -> assertThat(message.getText()).contains("SESSION SUMMARY"));
        assertThat(((OpenAiChatOptions) next.getOptions()).getMaxCompletionTokens()).isEqualTo(32_768);
    }

    @Test
    void freshSessionDoesNotReuseProviderUsageFromThePreviousHistory() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().maxCompletionTokens(32_768).build());
        when(model.call(any(Prompt.class))).thenReturn(textResponse("previous stage", 190_000), textResponse("fresh stage", 100));
        var runner = runner(model, 200_000);

        runner.runTextSession("system", null, "previous", 1, () -> false, null, null);
        runner.runTextSession("system", null, "fresh", 1, () -> false, null, null);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(((OpenAiChatOptions) prompts.getAllValues().getLast().getOptions()).getMaxCompletionTokens()).isEqualTo(32_768);
        assertThat(prompts.getAllValues().getLast().getInstructions()).hasSize(2);
    }

    private static AgentLoopRunner runner(ChatModel model, int window) {
        return new AgentLoopRunner(List.of(model), window, Duration.ofMinutes(5), new TestProviderFailureCooldown());
    }

    private static ChatResponse toolResponse(int promptTokens) {
        var assistant = AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall("write-1", "function", "write", "{}"))).build();
        return new ChatResponse(List.of(new Generation(assistant)), ChatResponseMetadata.builder().usage(new DefaultUsage(promptTokens, 10)).build());
    }

    private static ChatResponse textResponse(String text, int promptTokens) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), ChatResponseMetadata.builder().usage(new DefaultUsage(promptTokens, 10)).build());
    }

    static class SchemaTools implements SubmitVetoAware {

        @Tool(description = "Write a workspace file after inspecting its existing content. Supply each path, package, class, method, input, output, exception, example and test constraint.")
        public String write(String path, String packageName, String className, String methodName, String input, String output, String exception, String example,
                String testConstraint) {
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
