package de.tum.cit.aet.artemis.atlas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Shared test configuration for Atlas module tests.
 * Provides mocked Spring AI beans to avoid requiring Azure OpenAI credentials in tests.
 * This configuration is imported by AbstractAtlasIntegrationTest so all Atlas tests share the same Spring context.
 */
@TestConfiguration
public class AtlasTestConfiguration {

    /**
     * Provides a mocked ChatModel for testing.
     * Returns a default mocked response to avoid NullPointerExceptions in tests.
     *
     * @return mocked ChatModel instance
     */
    @Bean
    public ChatModel chatModel() {
        ChatModel mockChatModel = mock(ChatModel.class);
        when(mockChatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mocked AI response for testing")))));
        return mockChatModel;
    }

    /**
     * Provides a ChatClient using the mocked ChatModel.
     * This ensures the AtlasAgentService can function in tests without real Azure OpenAI connection.
     *
     * @param chatModel the mocked ChatModel
     * @return ChatClient instance built from the mocked ChatModel
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
