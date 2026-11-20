package de.tum.cit.aet.artemis.core.service.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.llm.LlmEnabled;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Endpoint;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.EndpointType;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties.Preset;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

/**
 * Builds the clients and chat models of the configured LLM presets. This is the only place in Artemis that connects to an LLM provider for chat:
 * a preset with {@code api: chat-completions} gets Spring AI's {@link OpenAiChatModel}, a preset with {@code api: responses} gets the
 * {@link OpenAiResponsesChatModel}. Both carry exactly the options the preset sets and use one provider client per endpoint.
 */
@Conditional(LlmEnabled.class)
@Lazy
@Service
public class LlmClientService {

    /** Response metadata key carrying the name of the preset that made the call. */
    public static final String PRESET_METADATA_KEY = "artemis.llm.preset";

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
     * @param presetName a configured preset with {@code api: responses} or {@code api: chat-completions}
     * @return a chat client over the preset's model
     */
    public ChatClient chatClient(String presetName) {
        return ChatClient.builder(chatModel(presetName)).build();
    }

    /**
     * @param presetName a configured preset with {@code api: responses} or {@code api: chat-completions}
     * @return the preset's chat model; every response carries the preset name under {@link #PRESET_METADATA_KEY}
     */
    public ChatModel chatModel(String presetName) {
        return chatModelsByPreset.computeIfAbsent(presetName, this::createChatModel);
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
        OpenAIClient client = client(preset);
        OpenAiChatOptions options = options(preset);
        // the startup validation ensures that every preset has an api
        ChatModel model = switch (preset.api()) {
            case RESPONSES -> new OpenAiResponsesChatModel(client, jsonMapper, options);
            case CHAT_COMPLETIONS ->
                OpenAiChatModel.builder().openAiClient(client).openAiClientAsync(client.async()).options(options).observationRegistry(observationRegistry).build();
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

    private OpenAIClient client(Preset preset) {
        Endpoint endpoint = properties.requiredEndpointOf(preset);
        OpenAIClient client = clientsByEndpoint.computeIfAbsent(preset.endpoint(), name -> createClient(endpoint));
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
