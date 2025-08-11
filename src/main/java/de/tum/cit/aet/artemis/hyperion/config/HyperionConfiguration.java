package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

@Configuration
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        // Build a default ChatClient; provider (Azure OpenAI) and deployment are configured via properties
        return builder.build();
    }
}
