package de.tum.cit.aet.artemis.core.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Configuration for Spring AI chat clients.
 * This configuration is enabled when either Atlas or Hyperion modules are enabled.
 * Manual configuration without auto-configuration.
 */
@Configuration
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
@Lazy
public class SpringAIConfiguration {

    @Autowired
    private Environment environment;

    /**
     * Provides a ChatModel bean. In test profile, returns a mock; in production, returns null (will be provided by Azure configuration).
     *
     * @return ChatModel instance (mock in tests, null in production)
     */
    @Bean
    @Lazy
    public ChatModel chatModel() {
        if (isTestProfile()) {
            ChatModel mockChatModel = mock(ChatModel.class);
            when(mockChatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mocked AI response for testing")))));
            return mockChatModel;
        }
        return null;
    }

    /**
     * Default Chat Client for AI features.
     * Uses the manually configured Azure OpenAI model if available.
     * In test profile, creates a ChatClient from the mocked ChatModel.
     *
     * @param azureOpenAiChatModel the Azure OpenAI chat model to use (optional)
     * @param chatModel            the ChatModel to use (mocked in tests)
     * @return a configured ChatClient with default options, or null if model is not available
     */
    @Bean
    @Lazy
    public ChatClient chatClient(@Autowired(required = false) AzureOpenAiChatModel azureOpenAiChatModel, @Autowired(required = false) ChatModel chatModel) {
        if (isTestProfile() && chatModel != null) {
            return ChatClient.create(chatModel);
        }
        if (azureOpenAiChatModel == null) {
            return null;
        }
        return ChatClient.builder(azureOpenAiChatModel).defaultOptions(AzureOpenAiChatOptions.builder().deploymentName("gpt-5-mini").temperature(1.0).build()).build();
    }

    /**
     * Check if the application is running in test profile.
     *
     * @return true if test profile is active
     */
    private boolean isTestProfile() {
        return environment != null && (environment.matchesProfiles("test") || environment.matchesProfiles("artemistest"));
    }

    /**
     * Condition that enables Spring AI configuration when either Atlas or Hyperion is enabled.
     */
    public static class SpringAIEnabled implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();
            return artemisConfigHelper.isAtlasEnabled(context.getEnvironment()) || artemisConfigHelper.isHyperionEnabled(context.getEnvironment());
        }
    }
}
