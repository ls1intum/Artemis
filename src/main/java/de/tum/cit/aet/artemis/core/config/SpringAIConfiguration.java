package de.tum.cit.aet.artemis.core.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration for Spring AI chat clients.
 * This configuration is enabled when either Atlas or Hyperion modules are enabled.
 */
@Configuration
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
@Lazy
public class SpringAIConfiguration {

    /**
     * Default Chat Client for AI features.
     * Uses Spring AI's autoconfiguration for Azure OpenAI integration.
     *
     * @param chatClientBuilder the auto-configured chat client builder from Spring AI
     * @return a configured ChatClient
     */
    @Bean
    @Lazy
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
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
