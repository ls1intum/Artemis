package de.tum.cit.aet.artemis.deimos.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Dedicated Spring AI configuration for the Deimos module.
 * <p>
 * Deimos uses its own {@link ChatClient} backed by its own {@link OpenAiChatModel} so that
 * the LLM endpoint and model are fully independent from the shared Atlas/Hyperion config.
 * This guarantees that student code is only ever sent to the self-hosted endpoint
 * configured under {@code artemis.deimos.llm.*}.
 */
@Configuration
@Conditional(DeimosEnabled.class)
@Lazy
public class DeimosLlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DeimosLlmConfiguration.class);

    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";

    /**
     * Creates a dedicated {@link OpenAiChatModel} for the Deimos module, isolated from the shared Spring AI auto-config.
     *
     * @param baseUrl         the LLM endpoint base URL
     * @param apiKey          the API key for authentication
     * @param completionsPath the completions path (stripped to a prefix, since the SDK appends {@code /chat/completions})
     * @param model           the model identifier
     * @param temperature     the sampling temperature
     * @param timeoutSeconds  the request timeout in seconds
     * @return a configured {@link OpenAiChatModel} for Deimos
     */
    @Bean
    @Qualifier("deimosChatModel")
    @Lazy
    public OpenAiChatModel deimosChatModel(@Value("${artemis.deimos.llm.base-url}") String baseUrl, @Value("${artemis.deimos.llm.api-key}") String apiKey,
            @Value("${artemis.deimos.llm.completions-path:/api/chat/completions}") String completionsPath, @Value("${artemis.deimos.llm.model}") String model,
            @Value("${artemis.deimos.llm.temperature:0}") double temperature, @Value("${artemis.deimos.llm.timeout-seconds:90}") long timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        String effectiveBaseUrl = toOpenAiCompatibleBaseUrl(baseUrl, completionsPath);
        log.info("Configuring Deimos ChatModel: base-url={}, model={}, temperature={}, timeout={}", effectiveBaseUrl, model, temperature, timeout);

        OpenAiChatOptions options = OpenAiChatOptions.builder().baseUrl(effectiveBaseUrl).apiKey(apiKey).model(model).temperature(temperature).timeout(timeout).build();
        return OpenAiChatModel.builder().options(options).build();
    }

    /**
     * Creates a {@link ChatClient} backed by the Deimos-specific {@link OpenAiChatModel}.
     *
     * @param chatModel the Deimos chat model
     * @return a configured {@link ChatClient} for Deimos
     */
    @Bean
    @Qualifier("deimosChatClient")
    @Lazy
    public ChatClient deimosChatClient(@Qualifier("deimosChatModel") OpenAiChatModel chatModel) {
        log.info("Configuring Deimos ChatClient");
        return ChatClient.builder(chatModel).build();
    }

    /**
     * Maps the legacy {@code base-url} + {@code completions-path} pair to the base URL expected by
     * the official OpenAI Java SDK, which appends {@code /chat/completions} itself.
     */
    static String toOpenAiCompatibleBaseUrl(String baseUrl, String completionsPath) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (completionsPath.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            String pathPrefix = completionsPath.substring(0, completionsPath.length() - CHAT_COMPLETIONS_SUFFIX.length());
            return normalizedBaseUrl + pathPrefix;
        }
        return normalizedBaseUrl;
    }
}
