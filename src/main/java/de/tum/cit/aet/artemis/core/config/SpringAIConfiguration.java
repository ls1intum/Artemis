package de.tum.cit.aet.artemis.core.config;

import jakarta.annotation.Nullable;

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

    private final int maxMessages;

    private final double temperature;

    private final double atlasAgentTemperature;

    private final String deploymentName;

    private final String atlasChatModel;

    public SpringAIConfiguration(@Value("${spring.ai.azure.openai.chat.options.deployment-name: gpt-5-mini}") String deploymentName,
            @Value("${spring.ai.azure.openai.chat.options.temperature: 1.0}") double temperature, @Value("${artemis.atlas.temperature: 0.2}") double atlasAgentTemperature,
            @Value("${spring.ai.chat.memory.max-messages: 20}") int maxMessages, @Value("${artemis.atlas.chat-model: gpt-4o}") String atlasChatModel) {
        this.deploymentName = deploymentName;
        this.temperature = temperature;
        this.maxMessages = maxMessages;
        this.atlasChatModel = atlasChatModel;
        this.atlasAgentTemperature = atlasAgentTemperature;

    }

    /**
     * Creates a JDBC-based chat memory repository for persistent storage.
     * Uses an auto-detected JDBC dialect based on the configured DataSource.
     * This bean is only created if no other ChatMemoryRepository bean exists,
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
     * Default ChatClient specifically configured for Hyperion.
     * Uses the model specified in spring.ai.azure.openai.chat.options.deployment-name (default: gpt-5-mini).
     * Does NOT include chat memory to ensure stateless operation.
     * This is the primary/default ChatClient bean that Hyperion services will inject.
     *
     * @param azureOpenAiChatModel the Azure OpenAI chat model to use (optional)
     * @return a configured ChatClient for Hyperion, or null if model is not available
     */
    @Bean
    @Lazy
    public ChatClient chatClient(@Nullable AzureOpenAiChatModel azureOpenAiChatModel) {
        if (azureOpenAiChatModel == null) {
            return null;
        }
        return ChatClient.builder(azureOpenAiChatModel).defaultOptions(AzureOpenAiChatOptions.builder().deploymentName(deploymentName).temperature(temperature).build()).build();
    }

    /**
     * ChatClient specifically configured for Atlas.
     * Uses the model specified in artemis.atlas.chat-model (default: gpt-4o).
     * Includes chat memory advisor for conversation context retention.
     *
     * @param azureOpenAiChatModel the Azure OpenAI chat model to use (optional)
     * @param chatMemory           the chat memory for conversation history (optional)
     * @return a configured ChatClient for Atlas, or null if model is not available
     */
    @Bean
    @Lazy
    public ChatClient atlasChatClient(@Nullable AzureOpenAiChatModel azureOpenAiChatModel, @Nullable ChatMemory chatMemory) {
        if (azureOpenAiChatModel == null) {
            return null;
        }
        ChatClient.Builder builder = ChatClient.builder(azureOpenAiChatModel)
                .defaultOptions(AzureOpenAiChatOptions.builder().deploymentName(atlasChatModel).temperature(atlasAgentTemperature).build());
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
        public boolean matches(ConditionContext context, @Nullable AnnotatedTypeMetadata metadata) {
            ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();
            return artemisConfigHelper.isAtlasEnabled(context.getEnvironment()) || artemisConfigHelper.isHyperionEnabled(context.getEnvironment());
        }
    }
}
