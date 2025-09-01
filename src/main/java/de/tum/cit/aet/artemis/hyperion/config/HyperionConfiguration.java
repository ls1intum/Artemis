package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionConfiguration {

    /**
     * ChatClient.Builder that chooses the model deterministically.
     * Prefers Azure if present, otherwise OpenAI.
     *
     * @param azure  the Azure OpenAI chat model bean, when available
     * @param openai the OpenAI chat model bean, when available
     * @return a ChatClient.Builder bound to the chosen model
     * @throws IllegalStateException if no ChatModel is available
     */
    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(@Qualifier("azureOpenAiChatModel") ObjectProvider<ChatModel> azure,
            @Qualifier("openAiChatModel") ObjectProvider<ChatModel> openai) {
        ChatModel model = azure.getIfAvailable();
        if (model == null) {
            model = openai.getIfAvailable();
        }
        if (model == null) {
            throw new IllegalStateException("No ChatModel beans available. Configure Spring AI OpenAI or Azure OpenAI.");
        }
        return ChatClient.builder(model);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
