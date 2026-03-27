package de.tum.cit.aet.artemis.core.config;

import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAIClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;

/**
 * Configuration for Spring AI chat clients.
 * This configuration is enabled when either Atlas or Hyperion modules are enabled.
 * Manual configuration without auto-configuration.
 */
@Configuration
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
@Lazy
public class SpringAIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpringAIConfiguration.class);

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
     * Default Chat Client for AI features.
     * Uses the manually configured Azure OpenAI model if available.
     * Includes memory advisor for conversation context retention.
     *
     * @param chatModels chat models that can be used (optional)
     * @return a configured ChatClient with default options, or null if model is not available
     */
    @Bean
    @Lazy
    public ChatClient chatClient(List<ChatModel> chatModels) {
        if (chatModels == null || chatModels.isEmpty()) {
            return null;
        }

        for (ChatModel model : chatModels) {
            if (model.getDefaultOptions() != null) {
                log.info("Found Chat Model: {} with options: {}", model.getDefaultOptions().getModel(), model.getDefaultOptions());
            }
            else {
                log.info("Found Chat Model: {} with no default options", model);
            }
        }
        ChatModel chatModel = chatModels.getFirst(); // Use the first available model
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        return builder.build();
    }

    /**
     * Applies explicit HTTP timeouts to the Azure OpenAI client builder.
     * Spring AI 1.1.3 does not bind the configured {@code spring.ai.azure.openai.client.*} timeout
     * properties automatically for the Azure model, so Artemis applies them explicitly here.
     *
     * @param connectTimeout  configured connection timeout
     * @param readTimeout     configured read timeout
     * @param responseTimeout configured response timeout
     * @return customizer that sets a Netty HTTP client with the configured timeouts
     */
    @Bean
    @Lazy
    @ConditionalOnClass({ OpenAIClientBuilder.class, AzureOpenAIClientBuilderCustomizer.class, NettyAsyncHttpClientBuilder.class })
    public AzureOpenAIClientBuilderCustomizer azureOpenAiClientTimeoutCustomizer(@Value("${spring.ai.azure.openai.client.connect-timeout:30s}") Duration connectTimeout,
            @Value("${spring.ai.azure.openai.client.read-timeout:5m}") Duration readTimeout,
            @Value("${spring.ai.azure.openai.client.response-timeout:5m}") Duration responseTimeout) {
        return clientBuilder -> {
            HttpClient httpClient = new NettyAsyncHttpClientBuilder().connectTimeout(connectTimeout).readTimeout(readTimeout).responseTimeout(responseTimeout).build();
            clientBuilder.httpClient(httpClient);
            log.info("Configured Azure OpenAI HTTP client timeouts: connect={}, read={}, response={}", connectTimeout, readTimeout, responseTimeout);
        };
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
