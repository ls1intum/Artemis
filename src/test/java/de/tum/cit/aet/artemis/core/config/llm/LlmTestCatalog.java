package de.tum.cit.aet.artemis.core.config.llm;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Price;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;

/**
 * Builds small LLM catalogs for tests.
 */
public final class LlmTestCatalog {

    /** The preset of {@link #priced}. */
    public static final String PRESET = "test";

    private LlmTestCatalog() {
    }

    /**
     * @param baseUrl the base URL
     * @param prices  the fallback prices
     * @return an OpenAI-type endpoint with a key and the default timeout and retries
     */
    public static Endpoint endpoint(String baseUrl, Price... prices) {
        return new Endpoint(EndpointType.OPENAI, baseUrl, "key", java.time.Duration.ofSeconds(300), 3, null, null, null, List.of(prices));
    }

    /**
     * @return a fallback price
     */
    public static Price price(String model, double input, @Nullable Double cachedInput, @Nullable Double cacheWrite, double output) {
        return new Price(model, input, cachedInput, cacheWrite, output);
    }

    /**
     * @return a preset without options
     */
    public static Preset preset(String endpoint, String model, Api api) {
        return new Preset(endpoint, model, api, null, null, null, null, null, null, null, null);
    }

    /**
     * @return a catalog with one endpoint {@code test} and one preset {@value #PRESET} for {@code model}, priced with {@code price} (or unpriced when null)
     */
    public static LlmProperties priced(String model, @Nullable Price price) {
        Endpoint endpoint = price == null ? endpoint("https://llm.example/v1") : endpoint("https://llm.example/v1", price);
        return new LlmProperties(Map.of("test", endpoint), Map.of(PRESET, preset("test", model, Api.CHAT_COMPLETIONS)), null);
    }

    /**
     * @return a catalog with one endpoint {@code embedding} and one {@code api: embeddings} preset {@code embedding} for {@code model}
     */
    public static LlmProperties embedding(String model, String baseUrl, @Nullable String apiKey) {
        Endpoint endpoint = new Endpoint(EndpointType.OPENAI, baseUrl, apiKey, java.time.Duration.ofSeconds(300), 3, null, null, null, List.of());
        return new LlmProperties(Map.of("embedding", endpoint), Map.of("embedding", preset("embedding", model, Api.EMBEDDINGS)), null);
    }

    /**
     * @return an empty catalog
     */
    public static LlmProperties empty() {
        return new LlmProperties(null, null, null);
    }

    /**
     * Marks a response as made by a preset, as {@link LlmClientService} does for every response of a preset's model.
     *
     * @return the marked response
     */
    public static ChatResponse tagged(ChatResponse response, String preset) {
        ChatResponseMetadata metadata = response.getMetadata();
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder().id(metadata.getId()).model(metadata.getModel()).usage(metadata.getUsage())
                .rateLimit(metadata.getRateLimit()).promptMetadata(metadata.getPromptMetadata());
        metadata.entrySet().forEach(entry -> builder.keyValue(entry.getKey(), entry.getValue()));
        builder.keyValue(LlmClientService.PRESET_METADATA_KEY, preset);
        return new ChatResponse(response.getResults(), builder.build());
    }
}
