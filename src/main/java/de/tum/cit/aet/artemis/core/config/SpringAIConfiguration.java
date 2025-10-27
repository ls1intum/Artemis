package de.tum.cit.aet.artemis.core.config;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.chat.client.ChatClient;
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
     *
     * @param azureOpenAiChatModel the Azure OpenAI chat model to use (optional)
     * @return a configured ChatClient with default options, or null if model is not available
     */
    @Bean
    @Lazy
    public ChatClient chatClient(@Autowired(required = false) AzureOpenAiChatModel azureOpenAiChatModel) {
        if (azureOpenAiChatModel == null) {
            return null;
        }
        return ChatClient.builder(azureOpenAiChatModel).build();
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
