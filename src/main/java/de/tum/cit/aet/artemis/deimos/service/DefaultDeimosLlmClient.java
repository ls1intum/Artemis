package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile(PROFILE_CORE)
@ConditionalOnProperty(name = "artemis.deimos.enabled", havingValue = "true")
@Lazy
@Service
public class DefaultDeimosLlmClient implements DeimosLlmClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultDeimosLlmClient.class);

    private final ChatClient chatClient;

    public DefaultDeimosLlmClient(@Qualifier("deimosChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public DeimosLlmResponse analyze(DeimosLlmRequest request) {
        ResponseEntity<ChatResponse, DeimosLlmResponse> responseResult = chatClient.prompt().system(request.systemPrompt()).user(request.userPrompt()).call()
                .responseEntity(DeimosLlmResponse.class);
        DeimosLlmResponse response = responseResult.entity();
        if (response == null) {
            throw new IllegalStateException("LLM returned null response for participation " + request.participationId());
        }
        return response;
    }
}
