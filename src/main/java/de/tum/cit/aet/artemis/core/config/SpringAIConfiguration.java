package de.tum.cit.aet.artemis.core.config;

import javax.sql.DataSource;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The chat memory of Spring AI, enabled when either Atlas or Hyperion is enabled. Chat models come from the LLM presets, see
 * {@link de.tum.cit.aet.artemis.core.service.llm.LlmClientService}.
 */
@Configuration
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
@Lazy
public class SpringAIConfiguration {

    private final int maxMessages;

    public SpringAIConfiguration(@Value("${spring.ai.chat.memory.max-messages: 20}") int maxMessages) {
        this.maxMessages = maxMessages;
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
     * Condition that enables Spring AI configuration when either the LLM-backed part of Atlas or Hyperion is enabled.
     * <p>
     * Atlas alone is not enough. Its competency features need no model, and every consumer of the beans below sits
     * behind one of these two flags, so keying on the Atlas module would build a chat memory repository on every
     * installation that has competencies. That is not free: it opens a connection and reads the database metadata to
     * pick a dialect, and the eager initialization of lazy singletons means it happens during startup rather than on
     * first use.
     */
    public static class SpringAIEnabled implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();
            return artemisConfigHelper.isAtlasLLMEnabled(context.getEnvironment()) || artemisConfigHelper.isHyperionEnabled(context.getEnvironment());
        }
    }
}
