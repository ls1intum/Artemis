package de.tum.cit.aet.artemis.atlas.agent;

import static org.mockito.Mockito.mock;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class AtlasAgentTestConfiguration {

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return mock(ChatModel.class);
    }

    @Bean
    @Primary
    public ChatClient testChatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
