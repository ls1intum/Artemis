package de.tum.cit.aet.artemis.deimos.config;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.deimos.exception.DeimosConfigurationException;

/**
 * Dedicated Spring AI configuration for the Deimos module.
 * <p>
 * Deimos uses its own {@link ChatClient} backed by its own {@link OpenAiChatModel} so that
 * the LLM endpoint and model are fully independent from the shared Atlas/Hyperion config.
 * This guarantees that student code is only ever sent to the self-hosted endpoint
 * configured under {@code artemis.deimos.llm.*}.
 * <p>
 * Retries are handled by exactly one layer: {@code artemis.deimos.llm.max-retries} is passed to
 * {@link OpenAiChatOptions}, which forwards it to the official OpenAI SDK. The SDK already retries timeouts,
 * I/O failures and HTTP 408/409/429/5xx with backoff. Deliberately no additional retry is layered on top,
 * because nested retries multiply the attempt count and can stall a large sequential batch.
 */
@Configuration
@Conditional(DeimosEnabled.class)
@Lazy
public class DeimosLlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DeimosLlmConfiguration.class);

    /**
     * The path segment the official OpenAI SDK appends to the base URL on its own.
     */
    public static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";

    /**
     * Default completions path, used when {@code artemis.deimos.llm.completions-path} is not set.
     */
    public static final String DEFAULT_COMPLETIONS_PATH = "/api" + CHAT_COMPLETIONS_SUFFIX;

    /**
     * Creates a dedicated {@link OpenAiChatModel} for the Deimos module, isolated from the shared Spring AI auto-config.
     *
     * @param baseUrl         the LLM endpoint base URL
     * @param apiKey          the API key for authentication; may be empty for an unauthenticated self-hosted endpoint
     * @param completionsPath the completions path (stripped to a prefix, since the SDK appends {@code /chat/completions})
     * @param model           the model identifier
     * @param temperature     the sampling temperature
     * @param timeoutSeconds  the request timeout in seconds
     * @param maxRetries      the number of transport-level retries performed by the OpenAI SDK
     * @return a configured {@link OpenAiChatModel} for Deimos
     */
    @Bean
    @Qualifier("deimosChatModel")
    @Lazy
    public OpenAiChatModel deimosChatModel(@Value("${artemis.deimos.llm.base-url}") String baseUrl, @Value("${artemis.deimos.llm.api-key:}") String apiKey,
            @Value("${artemis.deimos.llm.completions-path:" + DEFAULT_COMPLETIONS_PATH + "}") String completionsPath, @Value("${artemis.deimos.llm.model}") String model,
            @Value("${artemis.deimos.llm.temperature:0}") double temperature, @Value("${artemis.deimos.llm.timeout-seconds:90}") long timeoutSeconds,
            @Value("${artemis.deimos.llm.max-retries:3}") int maxRetries) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        String effectiveBaseUrl = toOpenAiCompatibleBaseUrl(baseUrl, completionsPath);
        log.info("Configuring Deimos ChatModel: base-url={}, model={}, temperature={}, timeout={}, max-retries={}", effectiveBaseUrl, model, temperature, timeout, maxRetries);

        OpenAiChatOptions options = OpenAiChatOptions.builder().baseUrl(effectiveBaseUrl).apiKey(apiKey).model(model).temperature(temperature).timeout(timeout)
                .maxRetries(maxRetries).build();
        return OpenAiChatModel.builder().options(options).build();
    }

    /**
     * Creates a {@link ChatClient} backed by the Deimos-specific chat model.
     * <p>
     * Declared against the {@link ChatModel} interface rather than {@link OpenAiChatModel}: the concrete type adds
     * nothing here, and requiring it makes the bean unresolvable in any context that substitutes the model, which is
     * what integration tests do.
     *
     * @param chatModel the Deimos chat model
     * @return a configured {@link ChatClient} for Deimos
     */
    @Bean
    @Qualifier("deimosChatClient")
    @Lazy
    public ChatClient deimosChatClient(@Qualifier("deimosChatModel") ChatModel chatModel) {
        log.info("Configuring Deimos ChatClient");
        return ChatClient.builder(chatModel).build();
    }

    /**
     * Maps the {@code base-url} + {@code completions-path} pair to the base URL expected by the official OpenAI Java SDK,
     * which appends {@code /chat/completions} itself.
     * <p>
     * A completions path that does not end with {@code /chat/completions} cannot be mapped and is rejected rather than
     * silently dropped: silently dropping it would send every request to the wrong endpoint, which for Deimos means
     * sending student source code somewhere the operator did not intend.
     *
     * @param baseUrl         the configured LLM endpoint base URL
     * @param completionsPath the configured completions path
     * @return the base URL to hand to the OpenAI SDK
     * @throws DeimosConfigurationException if the completions path cannot be mapped
     */
    public static String toOpenAiCompatibleBaseUrl(String baseUrl, String completionsPath) {
        if (completionsPath == null || !completionsPath.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            String message = "artemis.deimos.llm.completions-path '%s' must end with '%s'".formatted(completionsPath, CHAT_COMPLETIONS_SUFFIX);
            throw new DeimosConfigurationException(message, List.of("artemis.deimos.llm.completions-path"));
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String pathPrefix = completionsPath.substring(0, completionsPath.length() - CHAT_COMPLETIONS_SUFFIX.length());
        return normalizedBaseUrl + pathPrefix;
    }
}
