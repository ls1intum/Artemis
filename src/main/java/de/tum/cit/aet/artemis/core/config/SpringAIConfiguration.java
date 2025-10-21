package de.tum.cit.aet.artemis.core.config;

import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuration for Spring AI chat clients.
 * This configuration is enabled when either Atlas or Hyperion modules are enabled.
 * Manual configuration without auto-configuration.
 */
@Configuration
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
@Lazy
public class SpringAIConfiguration {

    @Value("${spring.ai.chat.memory.max-messages:20}")
    private int maxMessages;

    @Value("${spring.ai.azure.openai.chat.options.deployment-name:gpt-5-mini}")
    private String deploymentName;

    @Value("${spring.ai.azure.openai.chat.options.temperature:1.0}")
    private double temperature;

    /**
     * Creates a JDBC-based chat memory repository for persistent storage.
     * Uses MySQL dialect for database-specific operations.
     * This bean is only created if no other ChatMemoryRepository bean exists,
     * allowing Spring AI auto-configuration to take precedence if available.
     *
     * @param dataSource the datasource to use for JDBC operations
     * @return configured ChatMemoryRepository
     */
    @Bean
    @Lazy
    @ConditionalOnMissingBean
    public ChatMemoryRepository chatMemoryRepository(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return JdbcChatMemoryRepository.builder().jdbcTemplate(jdbcTemplate).dialect(JdbcChatMemoryRepositoryDialect.from(dataSource)).build();
    }

    /**
     * Creates a message window-based chat memory that retains the last N messages.
     * This provides context-aware conversations while limiting memory usage.
     *
     * @param chatMemoryRepository the repository for storing conversation history
     * @return configured ChatMemory with windowed message retention
     */
    @Bean
    @Lazy
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(maxMessages).build();
    }

    /**
     * Default Chat Client for AI features.
     * Uses the manually configured Azure OpenAI model if available.
     * Includes memory advisor for conversation context retention.
     *
     * @param azureOpenAiChatModel the Azure OpenAI chat model to use (optional)
     * @param chatMemory           the chat memory for conversation history (optional)
     * @return a configured ChatClient with default options, or null if model is not available
     */
    @Bean
    @Lazy
    public ChatClient chatClient(@Nullable AzureOpenAiChatModel azureOpenAiChatModel, @Nullable ChatMemory chatMemory) {
        if (azureOpenAiChatModel == null) {
            return null;
        }
        ChatClient.Builder builder = ChatClient.builder(azureOpenAiChatModel)
                .defaultOptions(AzureOpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature).build());

        // Add memory advisor if available
        if (chatMemory != null) {
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        }

        return builder.build();
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
