package de.tum.cit.aet.artemis.deimos.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

    @Bean
    @Qualifier("deimosOpenAiApi")
    @Lazy
    public OpenAiApi deimosOpenAiApi(@Value("${artemis.deimos.llm.base-url}") String baseUrl, @Value("${artemis.deimos.llm.api-key}") String apiKey,
            @Value("${artemis.deimos.llm.completions-path:/api/chat/completions}") String completionsPath) {
        log.info("Configuring Deimos OpenAI API: base-url={}, completions-path={}", baseUrl, completionsPath);
        return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).completionsPath(completionsPath).build();
    }

    @Bean
    @Qualifier("deimosChatModel")
    @Lazy
    public OpenAiChatModel deimosChatModel(@Qualifier("deimosOpenAiApi") OpenAiApi openAiApi, @Value("${artemis.deimos.llm.model}") String model,
            @Value("${artemis.deimos.llm.temperature:0.3}") double temperature) {
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
