package de.tum.cit.aet.artemis.core.config.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The catalog of LLM endpoints and presets under {@code artemis.llm}.
 * <p>
 * An endpoint describes where and how Artemis connects to a provider. A preset names one model on one endpoint, the API used to call it and the
 * request options. Modules assign presets to their tasks in their own configuration blocks ({@code llm-preset} or {@code llm-presets}); nothing in this
 * catalog has a default model, endpoint or price.
 *
 * @param endpoints  the endpoints by name
 * @param presets    the presets by name
 * @param modelCosts the removed {@code artemis.llm.model-costs} block, accepted and ignored for one release so that an old configuration only produces a
 *                       warning
 */
@ConfigurationProperties(prefix = "artemis.llm", ignoreUnknownFields = false)
public record LlmProperties(@Nullable Map<String, Endpoint> endpoints, @Nullable Map<String, Preset> presets, @Nullable Map<String, Object> modelCosts) {

    /** Names of endpoints and presets; environment variables cannot carry dashes or dots in map keys. */
    public static final String NAME_PATTERN = "[a-z][a-z0-9]*";

    public LlmProperties {
        endpoints = endpoints == null ? Map.of() : Map.copyOf(endpoints);
        presets = presets == null ? Map.of() : Map.copyOf(presets);
    }

    /**
     * Provider protocol of an endpoint.
     */
    public enum EndpointType {
        /** OpenAI or any OpenAI-compatible server, authenticated with a bearer key. */
        OPENAI,
        /** Azure OpenAI or Microsoft Foundry. */
        MICROSOFT_FOUNDRY
    }

    /**
     * API a preset calls.
     */
    public enum Api {
        RESPONSES, EMBEDDINGS
    }

    /**
     * Connection to one provider.
     *
     * @param type                           the provider protocol
     * @param baseUrl                        the base URL including the version prefix, for example {@code https://api.openai.com/v1}
     * @param apiKey                         the API key; empty sends no {@code Authorization} header
     * @param timeout                        the HTTP timeout of one call
     * @param maxRetries                     the SDK retries of one call
     * @param organizationId                 the OpenAI organization, if any
     * @param microsoftFoundryServiceVersion the Azure OpenAI service version, if any
     * @param customHeaders                  additional HTTP headers
     * @param pricesUsdPerMillion            fallback prices for providers that do not report the USD amount of a call
     */
    public record Endpoint(@DefaultValue("openai") EndpointType type, @Nullable String baseUrl, @Nullable String apiKey, @DefaultValue("300s") Duration timeout,
            @DefaultValue("3") int maxRetries, @Nullable String organizationId, @Nullable String microsoftFoundryServiceVersion, @Nullable Map<String, String> customHeaders,
            @Nullable List<Price> pricesUsdPerMillion) {

        public Endpoint {
            customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
            pricesUsdPerMillion = pricesUsdPerMillion == null ? List.of() : List.copyOf(pricesUsdPerMillion);
        }

        /**
         * @return whether a non-empty API key is configured
         */
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /**
         * Never prints the API key or header values.
         */
        @Override
        public String toString() {
            return "Endpoint[type=" + type + ", baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "***" : "none") + ", timeout=" + timeout + ", maxRetries=" + maxRetries
                    + ", organizationId=" + organizationId + ", microsoftFoundryServiceVersion=" + microsoftFoundryServiceVersion + ", customHeaders="
                    + customHeaders.keySet().stream().collect(Collectors.joining(", ", "[", "]")) + ", prices=" + pricesUsdPerMillion.size() + "]";
        }
    }

    /**
     * Fallback price of one model on one endpoint, in USD per million tokens.
     *
     * @param model       the configured model name of the presets this price applies to
     * @param input       uncached input tokens
     * @param cachedInput cache-read input tokens; the input price applies when unset
     * @param cacheWrite  cache-write input tokens; the input price applies when unset
     * @param output      output tokens
     */
    public record Price(@Nullable String model, @Nullable Double input, @Nullable Double cachedInput, @Nullable Double cacheWrite, @Nullable Double output) {
    }

    /**
     * One model with its API and request options.
     *
     * @param endpoint            the endpoint name
     * @param model               the model or deployment name sent to the provider
     * @param api                 the API that is called
     * @param reasoningEffort     the reasoning effort, if any
     * @param temperature         the sampling temperature; only without a reasoning effort or with {@code none}
     * @param topP                the nucleus sampling value, if any
     * @param verbosity           the response verbosity, if any
     * @param maxTokens           an alternative output token limit; at most one of the two limits
     * @param maxCompletionTokens the {@code max_completion_tokens} limit; the Responses API receives either limit as {@code max_output_tokens}
     * @param timeout             overrides the endpoint timeout
     * @param maxRetries          overrides the endpoint retries
     */
    public record Preset(@Nullable String endpoint, @Nullable String model, @Nullable Api api, @Nullable String reasoningEffort, @Nullable Double temperature,
            @Nullable Double topP, @Nullable String verbosity, @Nullable Integer maxTokens, @Nullable Integer maxCompletionTokens, @Nullable Duration timeout,
            @Nullable Integer maxRetries) {

        /**
         * @return the output limit in either field, or null
         */
        public @Nullable Integer outputTokenLimit() {
            return maxCompletionTokens != null ? maxCompletionTokens : maxTokens;
        }
    }

    /**
     * @param name the preset name
     * @return the preset, or null when it is not configured
     */
    public @Nullable Preset preset(String name) {
        return presets.get(name);
    }

    /**
     * @param preset a configured preset
     * @return its endpoint, or null when the preset names an unknown endpoint
     */
    public @Nullable Endpoint endpointOf(Preset preset) {
        return preset.endpoint() == null ? null : endpoints.get(preset.endpoint());
    }

    /**
     * @param preset a configured preset of a validated catalog
     * @return its endpoint
     * @throws IllegalStateException when the preset names an unknown endpoint, which the startup validation rejects
     */
    public Endpoint requiredEndpointOf(Preset preset) {
        Endpoint endpoint = endpointOf(preset);
        if (endpoint == null) {
            throw new IllegalStateException("The LLM preset names the unknown endpoint " + preset.endpoint());
        }
        return endpoint;
    }

    /**
     * @param preset a configured preset
     * @return the fallback price of its model on its endpoint, or null
     */
    public @Nullable Price priceOf(Preset preset) {
        Endpoint endpoint = endpointOf(preset);
        if (endpoint == null || preset.model() == null) {
            return null;
        }
        return endpoint.pricesUsdPerMillion().stream().filter(price -> Objects.equals(price.model(), preset.model())).findFirst().orElse(null);
    }

    /**
     * @param preset a configured preset
     * @return the effective timeout of a call made with this preset
     */
    public Duration effectiveTimeout(Preset preset) {
        return preset.timeout() != null ? preset.timeout() : requiredEndpointOf(preset).timeout();
    }

    /**
     * @param preset a configured preset
     * @return the effective SDK retries of a call made with this preset
     */
    public int effectiveMaxRetries(Preset preset) {
        return preset.maxRetries() != null ? preset.maxRetries() : requiredEndpointOf(preset).maxRetries();
    }
}
