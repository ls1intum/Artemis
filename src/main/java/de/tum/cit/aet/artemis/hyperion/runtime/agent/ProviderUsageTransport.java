package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.util.List;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;

/** Converts only reported usage fields, without transmitting or reconstructing provider content. */
public final class ProviderUsageTransport {

    private ProviderUsageTransport() {
    }

    /**
     * Captures provider accounting metadata without transporting prompt or completion content.
     *
     * @param response the actual provider response
     * @return the reported token counts and provider identifiers
     */
    public static ProviderUsageUpdate.Call capture(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            throw new IllegalArgumentException("Provider response contains no usage metadata");
        }
        var metadata = response.getMetadata();
        var usage = metadata.getUsage();
        if (usage.getPromptTokens() == null || usage.getCompletionTokens() == null) {
            throw new IllegalArgumentException("Provider response contains incomplete token counts");
        }
        return new ProviderUsageUpdate.Call(metadata.getModel() == null ? "" : metadata.getModel(), metadata.getId(), usage.getPromptTokens(), usage.getCompletionTokens(),
                usage.getCacheReadInputTokens());
    }

    /**
     * Adapts the reported fields to the existing core accounting consumer. The response intentionally has no generated content.
     *
     * @param call the fields actually returned by the provider
     * @return metadata-only response for core accounting
     */
    public static ChatResponse restore(ProviderUsageUpdate.Call call) {
        Usage usage = new Usage() {

            @Override
            public Integer getPromptTokens() {
                return call.inputTokens();
            }

            @Override
            public Integer getCompletionTokens() {
                return call.outputTokens();
            }

            @Override
            public Integer getTotalTokens() {
                return Math.addExact(call.inputTokens(), call.outputTokens());
            }

            @Override
            public Long getCacheReadInputTokens() {
                return call.cachedInputTokens();
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
        var metadata = ChatResponseMetadata.builder().model(call.model()).id(call.requestId() == null ? "" : call.requestId()).usage(usage).build();
        return new ChatResponse(List.of(), metadata);
    }
}
