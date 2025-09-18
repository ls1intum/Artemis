package de.tum.cit.aet.artemis.core.config;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

/**
 * Configuration for Spring AI chat clients.
 * This configuration is enabled when either Atlas or Hyperion modules are enabled.
 * Manual configuration without auto-configuration.
 */
@Configuration
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
@Lazy
public class SpringAIConfiguration {

    @Value("${spring.ai.azure.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.azure.openai.endpoint}")
    private String endpoint;

    @Value("${spring.ai.azure.openai.chat.options.deployment-name}")
    private String deploymentName;

    /**
     * Azure OpenAI Client bean - manually configured.
     */
    @Bean
    @Lazy
    public OpenAIClient openAIClient() {
        return new OpenAIClientBuilder().endpoint(endpoint).credential(new AzureKeyCredential(apiKey)).buildClient();
    }

    /**
     * Default Chat Client for AI features.
     * Uses the manually configured Azure OpenAI model.
     */
    @Bean
    @Lazy
    public ChatClient chatClient(AzureOpenAiChatModel azureOpenAiChatModel) {
        return ChatClient.builder(azureOpenAiChatModel).defaultOptions(AzureOpenAiChatOptions.builder().deploymentName(deploymentName).build()).build();
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
