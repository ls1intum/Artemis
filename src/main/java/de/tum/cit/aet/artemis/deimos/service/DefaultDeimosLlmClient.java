package de.tum.cit.aet.artemis.deimos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.deimos.config.DeimosEnabled;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmRequest;
import de.tum.cit.aet.artemis.deimos.dto.DeimosLlmResponse;

@Conditional(DeimosEnabled.class)
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
