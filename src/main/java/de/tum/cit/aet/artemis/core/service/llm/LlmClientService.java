package de.tum.cit.aet.artemis.core.service.llm;

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
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.core.config.llm.LlmEnabled;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpConnectionPoolMetrics;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationInterceptor;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.OkHttpClient;

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

    private final MeterRegistry meterRegistry;

    private final Map<String, OkHttpClient> connectionsByEndpoint = new ConcurrentHashMap<>();

    private record EmbeddingRequest(String model, List<String> input, @JsonProperty("encoding_format") String encodingFormat) implements LlmHttpClient.Request {
    }

    private final Map<String, ChatModel> chatModelsByPreset = new ConcurrentHashMap<>();

    public LlmClientService(LlmProperties properties, JsonMapper jsonMapper, ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.jsonMapper = LlmHttpClient.wireMapper(jsonMapper);
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
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
     * Embeds texts with an embeddings preset. The call may take at most five seconds, and never more than the timeout, to connect, then the timeout
     * for the answer. It makes no HTTP retries, so the caller decides whether and when to try again. It asks for float vectors and accepts providers that return base64.
     *
     * @param presetName a configured preset with {@code api: embeddings}
     * @param inputs     the texts to embed, in order
     * @param timeout    the positive time limit for the answer
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
        Duration connectTimeout = timeout.compareTo(MAX_EMBEDDING_CONNECT_TIMEOUT) < 0 ? timeout : MAX_EMBEDDING_CONNECT_TIMEOUT;
        var client = client(preset, connectTimeout, timeout, connectTimeout.plus(timeout), 0);
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
        var client = client(preset, Duration.ofMinutes(1), timeout, timeout, maxRetries);
        return new PresetTaggingChatModel(new OpenAiResponsesChatModel(client, jsonMapper, options(preset), observations), presetName);
    }

    private LlmHttpClient client(Preset preset, Duration connectTimeout, Duration readTimeout, Duration callTimeout, int maxRetries) {
        Endpoint endpoint = properties.requiredEndpointOf(preset);
        OkHttpClient shared = connectionsByEndpoint.computeIfAbsent(preset.endpoint(), name -> {
            var pool = new OkHttpClient.Builder().addInterceptor(OkHttpObservationInterceptor.builder(observationRegistry, "okhttp.requests").build())
                    .pingInterval(Duration.ofMinutes(1)).retryOnConnectionFailure(false).followRedirects(false).build();
            new OkHttpConnectionPoolMetrics(pool.connectionPool(), Tags.of("endpoint", name)).bindTo(meterRegistry);
            return pool;
        });
        // Derived clients share the endpoint's connections and dispatcher. The complete call deadline includes a slowly streaming body.
        var client = shared.newBuilder().connectTimeout(connectTimeout).readTimeout(readTimeout).writeTimeout(readTimeout).callTimeout(callTimeout).build();
        return new LlmHttpClient(endpoint, client, jsonMapper, maxRetries);
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
        connectionsByEndpoint.values().forEach(client -> {
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
        });
    }
}
