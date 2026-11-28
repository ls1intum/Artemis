package de.tum.cit.aet.artemis.atlas.service;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;

/** Records each provider response before the tool advisor combines rounds or executes another tool. */
final class AtlasTokenUsageAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AtlasTokenUsageAdvisor.class);

    private final Consumer<ChatResponse> recorder;

    private final int order;

    AtlasTokenUsageAdvisor(Consumer<ChatResponse> recorder, int toolAdvisorOrder) {
        this.recorder = recorder;
        this.order = toolAdvisorOrder + 1;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        if (response.chatResponse() != null) {
            try {
                recorder.accept(response.chatResponse());
            }
            catch (RuntimeException ex) {
                log.warn("Failed to record Atlas model-call usage", ex);
            }
        }
        return response;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return order;
    }
}
