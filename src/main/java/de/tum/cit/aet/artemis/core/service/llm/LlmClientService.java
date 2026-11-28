package de.tum.cit.aet.artemis.core.service.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.core.Timeout;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmEnabled;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Api;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

/**
 * Builds the clients and chat models of the configured LLM presets. This is the only place in Artemis that connects to an LLM provider, and
 * {@code LlmClientArchitectureTest} keeps it so. Responses presets use {@link OpenAiResponsesChatModel}, and embeddings presets use
 * {@link #embed}. Both use the configured options and share one provider client per endpoint.
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

    private final List<OpenAiHttpClientBuilderCustomizer> customizers;

    private final Map<String, OpenAIClient> clientsByEndpoint = new ConcurrentHashMap<>();

    private final Map<String, ChatModel> chatModelsByPreset = new ConcurrentHashMap<>();

    public LlmClientService(LlmProperties properties, JsonMapper jsonMapper, ObservationRegistry observationRegistry, MeterRegistry meterRegistry,
            List<OpenAiHttpClientBuilderCustomizer> customizers) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
        this.customizers = List.copyOf(customizers);
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
     * An isolated chat model of a preset, for a task with its own limits and data rules: its calls use the given time limit and SDK retries instead of the
     * preset's, and the model records no observations, so prompts and completions never reach tracing. It shares the endpoint's connections and is not
     * cached.
     *
     * @param presetName a configured preset with {@code api: responses}
     * @param timeout    the time limit of one call
     * @param maxRetries the SDK retries of one call
     * @return the chat model; every response carries the preset name under {@link #PRESET_METADATA_KEY}
     */
    public ChatModel isolatedChatModel(String presetName, Duration timeout, int maxRetries) {
        Preset preset = preset(presetName);
        OpenAIClient client = endpointClient(preset).withOptions(options -> options.timeout(timeout).maxRetries(maxRetries));
        return chatModel(presetName, preset, client, ObservationRegistry.NOOP);
    }

    /**
     * Embeds texts with an embeddings preset. The call may take at most five seconds, and never more than the timeout, to connect, then the timeout
     * for the answer. It makes no SDK retries, so the caller decides whether and when to try again. It asks for float vectors, the default of the embeddings API; without that, the
     * SDK would ask for base64.
     *
     * @param presetName a configured preset with {@code api: embeddings}
     * @param inputs     the texts to embed, in order
     * @param timeout    the positive time limit for the answer
     * @return the provider's response; it is not validated, so reading a field the provider left out throws an {@code OpenAIInvalidDataException}
     * @throws IllegalArgumentException when the preset is not configured, is not an embeddings preset, or the timeout is not positive
     */
    public CreateEmbeddingResponse embed(String presetName, List<String> inputs, Duration timeout) {
        if (timeout == null || !timeout.isPositive()) {
            throw new IllegalArgumentException("The embedding timeout must be positive");
        }
        Preset preset = preset(presetName);
        if (preset.api() != Api.EMBEDDINGS) {
            throw new IllegalArgumentException("The LLM preset " + presetName + " is not an embeddings preset");
        }
        // as a plain HTTP client would: at most five seconds (and never more than the timeout) to connect, then the timeout for the answer
        Duration connectTimeout = timeout.compareTo(MAX_EMBEDDING_CONNECT_TIMEOUT) < 0 ? timeout : MAX_EMBEDDING_CONNECT_TIMEOUT;
        Timeout limits = Timeout.builder().connect(connectTimeout).read(timeout).write(timeout).request(connectTimeout.plus(timeout)).build();
        OpenAIClient client = endpointClient(preset).withOptions(options -> options.timeout(limits).maxRetries(0));
        EmbeddingCreateParams params = EmbeddingCreateParams.builder().model(preset.model()).inputOfArrayOfStrings(inputs)
                .encodingFormat(EmbeddingCreateParams.EncodingFormat.FLOAT).build();
        CreateEmbeddingResponse response = client.embeddings().create(params);
        // without response validation the SDK passes a JSON null body through
        if (response == null) {
            throw new OpenAIInvalidDataException("The embeddings response of preset " + presetName + " is empty");
        }
        return response;
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
     * @return the effective SDK retries of the preset's calls
     */
    public int effectiveMaxRetries(String presetName) {
        return properties.effectiveMaxRetries(preset(presetName));
    }

    /**
     * @param presetName a configured preset
     * @return the effective timeout of the preset's calls
     */
    public Duration effectiveTimeout(String presetName) {
        return properties.effectiveTimeout(preset(presetName));
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
        return chatModel(presetName, preset, client(preset), observationRegistry);
    }

    private ChatModel chatModel(String presetName, Preset preset, OpenAIClient client, ObservationRegistry observations) {
        ChatModel model = switch (preset.api()) {
            case RESPONSES -> new OpenAiResponsesChatModel(client, jsonMapper, options(preset), observations);
            case EMBEDDINGS -> throw new IllegalArgumentException("The LLM preset " + presetName + " is an embeddings preset and has no chat model");
        };
        return new PresetTaggingChatModel(model, presetName);
    }

    private static OpenAiChatOptions options(Preset preset) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        options.model(preset.model());
        options.reasoningEffort(preset.reasoningEffort());
        options.temperature(preset.temperature());
        options.topP(preset.topP());
        options.verbosity(preset.verbosity());
        options.maxTokens(preset.maxTokens());
        options.maxCompletionTokens(preset.maxCompletionTokens());
        return options.build();
    }

    private OpenAIClient endpointClient(Preset preset) {
        Endpoint endpoint = properties.requiredEndpointOf(preset);
        return clientsByEndpoint.computeIfAbsent(preset.endpoint(), name -> createClient(endpoint));
    }

    private OpenAIClient client(Preset preset) {
        OpenAIClient client = endpointClient(preset);
        if (preset.timeout() == null && preset.maxRetries() == null) {
            return client;
        }
        return client.withOptions(options -> options.timeout(properties.effectiveTimeout(preset)).maxRetries(properties.effectiveMaxRetries(preset)));
    }

    private OpenAIClient createClient(Endpoint endpoint) {
        // An empty key (never null) keeps the SDK from falling back to credentials from the environment and sends no Authorization header.
        String apiKey = endpoint.hasApiKey() ? endpoint.apiKey() : "";
        AzureOpenAIServiceVersion serviceVersion = endpoint.microsoftFoundryServiceVersion() == null || endpoint.microsoftFoundryServiceVersion().isBlank() ? null
                : AzureOpenAIServiceVersion.fromString(endpoint.microsoftFoundryServiceVersion());
        return OpenAiSetup.setupSyncClient(endpoint.baseUrl(), apiKey, null, null, serviceVersion, endpoint.organizationId(), endpoint.type() == EndpointType.MICROSOFT_FOUNDRY,
                false, null, endpoint.timeout(), endpoint.maxRetries(), null, endpoint.customHeaders(), observationRegistry, meterRegistry, customizers);
    }
}
