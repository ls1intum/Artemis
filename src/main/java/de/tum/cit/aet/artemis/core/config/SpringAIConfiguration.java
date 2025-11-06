package de.tum.cit.aet.artemis.core.config;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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

    /**
     * Default Chat Client for AI features.
     * Uses the manually configured chat model if available, with specific optimizations for Azure OpenAI.
     *
     * @param chatModel the chat model to use (optional) - can be AzureOpenAiChatModel in production or generic ChatModel in tests
     * @return a configured ChatClient with Azure-specific options if using AzureOpenAiChatModel,
     *         basic configuration for other ChatModel implementations, or null if no model is available
     */
    @Bean
    @Lazy
    public ChatClient chatClient(@Autowired(required = false) ChatModel chatModel) {
        if (chatModel == null) {
            return null;
        }

        // If it's an AzureOpenAiChatModel, use specific options
        if (chatModel instanceof AzureOpenAiChatModel azureModel) {
            return ChatClient.builder(azureModel).defaultOptions(AzureOpenAiChatOptions.builder().deploymentName("gpt-5-mini").temperature(1.0).build()).build();
        }

        // For generic ChatModel (including mocks), use basic builder
        return ChatClient.builder(chatModel).build();
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
