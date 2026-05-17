package de.tum.cit.aet.artemis.deimos.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Dedicated Spring AI configuration for the Deimos module.
 * <p>
 * Deimos uses its own {@link ChatClient} backed by its own {@link OpenAiApi} so that
 * the LLM endpoint and model are fully independent from the shared Atlas/Hyperion config.
 * This guarantees that student code is only ever sent to the self-hosted endpoint
 * configured under {@code artemis.deimos.llm.*}.
 */
@Configuration
@Profile(PROFILE_CORE)
@ConditionalOnProperty(name = "artemis.deimos.enabled", havingValue = "true")
@Lazy
public class DeimosLlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DeimosLlmConfiguration.class);

    /**
     * Creates the dedicated OpenAI API client used by Deimos with bounded connect/read timeouts.
     *
     * @param baseUrl         the base URL of the self-hosted LLM endpoint
     * @param apiKey          the API key used for authentication
     * @param completionsPath the completions endpoint path
     * @param timeoutSeconds  timeout in seconds applied to connect/read operations
     * @return configured OpenAI API client for Deimos
     */
    @Bean
    @Qualifier("deimosOpenAiApi")
    @Lazy
    public OpenAiApi deimosOpenAiApi(@Value("${artemis.deimos.llm.base-url}") String baseUrl, @Value("${artemis.deimos.llm.api-key}") String apiKey,
            @Value("${artemis.deimos.llm.completions-path:/api/chat/completions}") String completionsPath, @Value("${artemis.deimos.llm.timeout-seconds:90}") long timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        log.info("Configuring Deimos OpenAI API: base-url={}, completions-path={}, timeout={}", baseUrl, completionsPath, timeout);

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);

        return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).completionsPath(completionsPath).restClientBuilder(restClientBuilder).build();
    }

    @Bean
    @Qualifier("deimosChatModel")
    @Lazy
    public OpenAiChatModel deimosChatModel(@Qualifier("deimosOpenAiApi") OpenAiApi openAiApi, @Value("${artemis.deimos.llm.model}") String model,
            @Value("${artemis.deimos.llm.temperature:0}") double temperature) {
        log.info("Configuring Deimos ChatModel: model={}, temperature={}", model, temperature);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).temperature(temperature).build();
        return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(options).build();
    }

    @Bean
    @Qualifier("deimosChatClient")
    @Lazy
    public ChatClient deimosChatClient(@Qualifier("deimosChatModel") OpenAiChatModel chatModel) {
        log.info("Configuring Deimos ChatClient");
        return ChatClient.builder(chatModel).build();
    }
}
