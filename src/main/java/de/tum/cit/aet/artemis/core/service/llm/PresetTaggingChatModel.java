package de.tum.cit.aet.artemis.core.service.llm;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;

/**
 * Marks every response of a preset's model with the preset name, so that usage accounting can price the call with that preset's endpoint price list
 * when the provider reports no USD amount. Spring AI's tool-calling advisor keeps custom response metadata when it accumulates usage.
 */
final class PresetTaggingChatModel implements ChatModel {

    private final ChatModel delegate;

    private final String preset;

    PresetTaggingChatModel(ChatModel delegate, String preset) {
        this.delegate = delegate;
        this.preset = preset;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return tag(delegate.call(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(prompt).map(this::tag);
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    private @Nullable ChatResponse tag(@Nullable ChatResponse response) {
        // pass on what the delegate returned; this wrapper only adds the preset name
        if (response == null) {
            return null;
        }
        ChatResponseMetadata metadata = response.getMetadata();
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder().id(metadata.getId()).model(metadata.getModel()).usage(metadata.getUsage())
                .rateLimit(metadata.getRateLimit()).promptMetadata(metadata.getPromptMetadata());
        metadata.entrySet().forEach(entry -> builder.keyValue(entry.getKey(), entry.getValue()));
        builder.keyValue(LlmClientService.PRESET_METADATA_KEY, preset);
        return new ChatResponse(response.getResults(), builder.build());
    }
}
