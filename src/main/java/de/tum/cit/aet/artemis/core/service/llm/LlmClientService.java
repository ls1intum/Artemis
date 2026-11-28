package de.tum.cit.aet.artemis.core.service.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.core.config.llm.LlmEnabled;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import io.micrometer.observation.ObservationRegistry;

/**
 * Builds the clients and chat models of the configured LLM presets. This is the only place in Artemis that connects to an LLM provider, and
 * {@code LlmClientArchitectureTest} keeps it so. Responses presets use {@link OpenAiResponsesChatModel}, and embeddings presets use
 * {@link #embed}. Both use the configured endpoint and preset options.
 */
@Conditional(LlmEnabled.class)
@Lazy
@Service
public class LlmClientService {

    /** Response metadata key carrying the name of the preset that made the call. */
    public static final String PRESET_METADATA_KEY = "artemis.llm.preset";

    private static final Duration MAX_EMBEDDING_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final LlmProperties properties;

    private final JsonMapper jsonMapper;

    private final ObservationRegistry observationRegistry;

    private final Map<Api, HttpClient> connectionsByApi = new ConcurrentHashMap<>();

    private record EmbeddingRequest(String model, List<String> input, @JsonProperty("encoding_format") String encodingFormat) implements LlmHttpClient.Request {
    }

    private final Map<String, ChatModel> chatModelsByPreset = new ConcurrentHashMap<>();

    public LlmClientService(LlmProperties properties, JsonMapper jsonMapper, ObservationRegistry observationRegistry) {
        this.properties = properties;
        this.jsonMapper = LlmHttpClient.wireMapper(jsonMapper);
        this.observationRegistry = observationRegistry;
    }

    /**
     * @param presetName a configured preset with {@code api: responses}
     * @return a chat client over the preset's model
     */
    public ChatClient chatClient(String presetName) {
        return ChatClient.builder(chatModel(presetName)).build();
    }

    /**
     * @param presetName a configured preset with {@code api: responses}
     * @return the preset's chat model; every response carries the preset name under {@link #PRESET_METADATA_KEY}
     */
    public ChatModel chatModel(String presetName) {
        return chatModelsByPreset.computeIfAbsent(presetName, this::createChatModel);
    }

    /**
     * An isolated chat model of a preset, for a task with its own limits and data rules: its calls use the given time limit and HTTP retries instead of the
     * preset's, and the model records no observations, so prompts and completions never reach tracing. It shares the endpoint's connections and is not
     * cached.
     *
     * @param presetName a configured preset with {@code api: responses}
     * @param timeout    the time limit of one call
     * @param maxRetries the HTTP retries of one call
     * @return the chat model; every response carries the preset name under {@link #PRESET_METADATA_KEY}
     */
    public ChatModel isolatedChatModel(String presetName, Duration timeout, int maxRetries) {
        Preset preset = preset(presetName);
        return chatModel(presetName, preset, timeout, maxRetries, ObservationRegistry.NOOP);
    }

    /**
     * Embeds texts with an embeddings preset. The timeout bounds the entire request, including connection setup and reading the response body.
     * Connection setup is also limited to five seconds. It makes no HTTP retries, so the caller decides whether and when to try again. It asks for float vectors and accepts
     * providers that return base64.
     *
     * @param presetName a configured preset with {@code api: embeddings}
     * @param inputs     the texts to embed, in order
     * @param timeout    the positive time limit for one request
     * @return the vectors and input indices reported by the provider
     * @throws IllegalArgumentException when the preset is not configured, is not an embeddings preset, or the timeout is not positive
     */
    public LlmEmbeddingResponse embed(String presetName, List<String> inputs, Duration timeout) {
        if (timeout == null || !timeout.isPositive()) {
            throw new IllegalArgumentException("The embedding timeout must be positive");
        }
        Preset preset = preset(presetName);
        if (preset.api() != Api.EMBEDDINGS) {
            throw new IllegalArgumentException("The LLM preset " + presetName + " is not an embeddings preset");
        }
        var client = client(preset, timeout, 0);
        return client.post("embeddings", new EmbeddingRequest(preset.model(), inputs, "float"), LlmEmbeddingResponse.class);
    }

    /**
     * @param presetName a configured preset
     * @return the preset
     * @throws IllegalArgumentException when the preset is not configured
     */
    public Preset preset(String presetName) {
        Preset preset = properties.preset(presetName);
        if (preset == null) {
            throw new IllegalArgumentException("The LLM preset " + presetName + " is not configured under artemis.llm.presets");
        }
        return preset;
    }

    /**
     * @param presetName a configured preset
     * @return the effective HTTP retries of the preset's calls
     */
    public int effectiveMaxRetries(String presetName) {
        return properties.effectiveMaxRetries(preset(presetName));
    }

    /**
     * Reads the preset that made a call from its response metadata.
     *
     * @param metadata the response metadata
     * @return the preset name, or null when the response did not come from a preset's model
     */
    public static @Nullable String presetOf(@Nullable ChatResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Object preset = metadata.get(PRESET_METADATA_KEY);
        return preset instanceof String name ? name : null;
    }

    private ChatModel createChatModel(String presetName) {
        Preset preset = preset(presetName);
        return chatModel(presetName, preset, properties.effectiveTimeout(preset), properties.effectiveMaxRetries(preset), observationRegistry);
    }

    private ChatModel chatModel(String presetName, Preset preset, Duration timeout, int maxRetries, ObservationRegistry observations) {
        if (preset.api() != Api.RESPONSES) {
            throw new IllegalArgumentException("The LLM preset " + presetName + " is an embeddings preset and has no chat model");
        }
        var client = client(preset, timeout, maxRetries);
        return new PresetTaggingChatModel(new OpenAiResponsesChatModel(client, jsonMapper, options(preset), observations), presetName);
    }

    private LlmHttpClient client(Preset preset, Duration timeout, int maxRetries) {
        Endpoint endpoint = properties.requiredEndpointOf(preset);
        // The JDK pools connections by origin. Chat and embeddings have different connection limits.
        HttpClient connections = connectionsByApi.computeIfAbsent(preset.api(), api -> HttpClient.newBuilder()
                .connectTimeout(api == Api.EMBEDDINGS ? MAX_EMBEDDING_CONNECT_TIMEOUT : Duration.ofMinutes(1)).followRedirects(HttpClient.Redirect.NEVER).build());
        var factory = new JdkClientHttpRequestFactory(connections);
        factory.setReadTimeout(timeout);
        return new LlmHttpClient(endpoint, factory, jsonMapper, observationRegistry, maxRetries);
    }

    private static LlmChatOptions options(Preset preset) {
        var options = LlmChatOptions.builder();
        options.model(preset.model());
        options.reasoningEffort(preset.reasoningEffort());
        options.temperature(preset.temperature());
        options.topP(preset.topP());
        options.verbosity(preset.verbosity());
        options.maxTokens(preset.maxTokens());
        options.maxCompletionTokens(preset.maxCompletionTokens());
        return options.build();
    }

    @PreDestroy
    void closeConnections() {
        connectionsByApi.values().forEach(HttpClient::shutdownNow);
    }
}
