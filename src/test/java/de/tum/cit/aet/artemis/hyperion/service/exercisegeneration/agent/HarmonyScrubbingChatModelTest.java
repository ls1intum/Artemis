package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * Unit test for the pure, HTTP-free {@link HarmonyScrubbingChatModel} decorator that replaced the prototype's bespoke {@code GpuEndpointChatModel}. It locks the two behaviours the
 * long-running agent loop depends on: gpt-oss "harmony" control tokens are stripped from the assistant {@code content} while tool calls and metadata are preserved verbatim, and a
 * clean response is returned unchanged (same instance) so healthy runs pay nothing. The wire transport is now the stock Spring AI OpenAI starter, so only the scrubber is tested.
 */
class HarmonyScrubbingChatModelTest {

    @Test
    void sanitizeHarmonyTokens_removesEveryControlToken() {
        String raw = "<|start|>assistant<|channel|>commentary<|message|>real answer<|end|>";

        assertThat(HarmonyScrubbingChatModel.sanitizeHarmonyTokens(raw)).isEqualTo("assistantcommentaryreal answer");
    }

    @Test
    void sanitizeHarmonyTokens_withoutToken_returnsSameReference() {
        String clean = "a perfectly ordinary assistant reply";

        // No allocation and no rewrite when there is nothing to scrub.
        assertThat(HarmonyScrubbingChatModel.sanitizeHarmonyTokens(clean)).isSameAs(clean);
        assertThat(HarmonyScrubbingChatModel.sanitizeHarmonyTokens(null)).isNull();
    }

    @Test
    void scrub_stripsTokensFromContentButPreservesToolCallsAndMetadata() {
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("call_1", "function", "bash", "{\"command\":\"ls\"}");
        AssistantMessage dirty = AssistantMessage.builder().content("plan<|channel|>commentary next").properties(Map.of("finishReason", "tool_calls")).toolCalls(List.of(toolCall))
                .build();
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().model("openai/gpt-oss-120b").build();
        ChatResponse response = new ChatResponse(List.of(new Generation(dirty)), metadata);

        ChatResponse scrubbed = HarmonyScrubbingChatModel.scrub(response);

        AssistantMessage cleaned = scrubbed.getResult().getOutput();
        // Control token removed, surrounding text kept.
        assertThat(cleaned.getText()).isEqualTo("plancommentary next");
        // Tool calls survive so the loop can still dispatch them.
        assertThat(cleaned.getToolCalls()).containsExactly(toolCall);
        // Per-message properties and the response-level metadata are carried over unchanged.
        assertThat(cleaned.getMetadata()).containsEntry("finishReason", "tool_calls");
        assertThat(scrubbed.getMetadata()).isSameAs(metadata);
    }

    @Test
    void scrub_cleanResponse_returnsSameInstance() {
        ChatResponse clean = new ChatResponse(List.of(new Generation(new AssistantMessage("nothing to strip here"))));

        // A run with no leaked harmony token must not be rebuilt at all.
        assertThat(HarmonyScrubbingChatModel.scrub(clean)).isSameAs(clean);
    }

    @Test
    void scrub_nullOrEmptyResults_returnedUnchanged() {
        assertThat(HarmonyScrubbingChatModel.scrub(null)).isNull();

        ChatResponse empty = new ChatResponse(List.of());
        assertThat(HarmonyScrubbingChatModel.scrub(empty)).isSameAs(empty);
    }

    @Test
    void call_scrubsTheDelegateResponse() {
        ChatModel delegate = mock(ChatModel.class);
        ChatResponse dirty = new ChatResponse(List.of(new Generation(new AssistantMessage("answer<|end|>"))));
        when(delegate.call(any(Prompt.class))).thenReturn(dirty);
        HarmonyScrubbingChatModel model = new HarmonyScrubbingChatModel(delegate);

        ChatResponse result = model.call(new Prompt("hi"));

        assertThat(result.getResult().getOutput().getText()).isEqualTo("answer");
    }

    @Test
    void getDefaultOptions_delegatesUnchanged() {
        ChatModel delegate = mock(ChatModel.class);
        ChatOptions options = mock(ChatOptions.class);
        when(delegate.getDefaultOptions()).thenReturn(options);
        HarmonyScrubbingChatModel model = new HarmonyScrubbingChatModel(delegate);

        // A straight pass-through so the loop can read the configured model id.
        assertThat(model.getDefaultOptions()).isSameAs(options);
    }

    @Test
    void stream_scrubsEachChunk() {
        ChatModel delegate = mock(ChatModel.class);
        Prompt prompt = new Prompt("hi");
        ChatResponse dirtyChunk = new ChatResponse(List.of(new Generation(new AssistantMessage("chunk<|end|>"))));
        when(delegate.stream(prompt)).thenReturn(Flux.just(dirtyChunk));
        HarmonyScrubbingChatModel model = new HarmonyScrubbingChatModel(delegate);

        List<ChatResponse> chunks = model.stream(prompt).collectList().block();
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getResult().getOutput().getText()).isEqualTo("chunk");
    }
}
