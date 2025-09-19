package de.tum.cit.aet.artemis.core.config;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

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
     * Uses the manually configured Azure OpenAI model.
     *
     * @param azureOpenAiChatModel the Azure OpenAI chat model to use
     * @return a configured ChatClient with default options
     */
    @Bean
    @Lazy
    public ChatClient chatClient(AzureOpenAiChatModel azureOpenAiChatModel) {
        if (deploymentName == null || deploymentName.trim().isEmpty()) {
            throw new IllegalStateException("Azure OpenAI deployment name is required but not configured");
        }
        return ChatClient.builder(azureOpenAiChatModel).defaultOptions(AzureOpenAiChatOptions.builder().deploymentName("gpt-5-mini").temperature(1.0).build()).build();
    }

    /**
     * Condition that enables Spring AI configuration when either Atlas or Hyperion is enabled.
     */
    public static class SpringAIEnabled implements org.springframework.context.annotation.Condition {

        private final ArtemisConfigHelper artemisConfigHelper;

        public SpringAIEnabled() {
            this.artemisConfigHelper = new ArtemisConfigHelper();
        }

        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context, org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            return artemisConfigHelper.isAtlasEnabled(context.getEnvironment()) || artemisConfigHelper.isHyperionEnabled(context.getEnvironment());
        }
    }
}
