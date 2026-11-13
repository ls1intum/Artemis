package de.tum.cit.aet.artemis.hyperion.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Provides the language models of the quiz question pool. The pool calls an OpenAI-compatible endpoint with the models configured under
 * {@code artemis.hyperion.quiz-pool} ({@code generator-model}, {@code judge-model}, {@code allowed-models}, {@code timeout}). The endpoint
 * is the pool's own {@code base-url} and {@code api-key} when set, and otherwise the endpoint of Hyperion's chat model
 * ({@code spring.ai.openai.base-url} and {@code api-key}).
 * <p>
 * Without both model names, or without any endpoint, the pool is not configured: builds are refused and no model is called. The same holds
 * when the endpoint lies on a known cloud provider or is a Microsoft Foundry endpoint, when only one model is set, or when a model is not
 * listed in {@code allowed-models}. At startup the endpoint is asked for its models ({@code GET /models}); when it answers and does not serve a
 * configured model, the pool is off as well. When it cannot be asked, the pool stays on. Each of these cases is logged as a warning that
 * names the setting to fix.
 */
@Service
@Lazy(false)
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolModelService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolModelService.class);

    private static final String PROPERTY_PREFIX = "artemis.hyperion.quiz-pool.";

    /** Host names, and the domains below them, of hosted model providers that the pool must never send course material to. */
    static final Set<String> CLOUD_HOSTS = Set.of("openai.com", "openai.azure.com", "cognitiveservices.azure.com", "services.ai.azure.com", "models.inference.ai.azure.com",
            "anthropic.com", "googleapis.com", "mistral.ai", "cohere.com", "cohere.ai", "groq.com", "together.xyz", "together.ai", "openrouter.ai", "deepseek.com", "x.ai",
            "fireworks.ai", "perplexity.ai", "amazonaws.com");

    private static final String OPENAI_CHAT_MODEL_PROVIDER = "openai";

    /** Timeouts of the startup request that lists the endpoint's models, short so an unreachable endpoint delays startup only briefly. */
    private static final Duration MODEL_LIST_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration MODEL_LIST_READ_TIMEOUT = Duration.ofSeconds(10);

    /** Sent when the endpoint needs no key, so the client never falls back to a key from the environment. */
    private static final String NO_API_KEY = "none";

    @Nullable
    private final ChatClient chatClient;

    private final String generatorModel;

    private final String judgeModel;

    public HyperionQuizQuestionPoolModelService(RestClient.Builder restClientBuilder, @Value("${" + PROPERTY_PREFIX + "base-url:}") String baseUrl,
            @Value("${" + PROPERTY_PREFIX + "api-key:}") String apiKey, @Value("${" + PROPERTY_PREFIX + "timeout:5m}") Duration timeout,
            @Value("${" + PROPERTY_PREFIX + "generator-model:}") String generatorModel, @Value("${" + PROPERTY_PREFIX + "judge-model:}") String judgeModel,
            @Value("${" + PROPERTY_PREFIX + "allowed-models:}") List<String> allowedModels, @Value("${spring.ai.model.chat:}") String chatModelProvider,
            @Value("${spring.ai.openai.base-url:}") String chatBaseUrl, @Value("${spring.ai.openai.api-key:}") String chatApiKey,
            @Value("${spring.ai.openai.microsoft-foundry:false}") boolean chatMicrosoftFoundry) {
        this.generatorModel = trim(generatorModel);
        this.judgeModel = trim(judgeModel);
        this.chatClient = createChatClient(restClientBuilder, trim(baseUrl), trim(apiKey), timeout, allowedModels, trim(chatModelProvider), trim(chatBaseUrl), trim(chatApiKey),
                chatMicrosoftFoundry);
    }

    private @Nullable ChatClient createChatClient(RestClient.Builder restClientBuilder, String baseUrl, String apiKey, Duration timeout, List<String> allowedModels,
            String chatModelProvider, String chatBaseUrl, String chatApiKey, boolean chatMicrosoftFoundry) {
        if (generatorModel.isEmpty() && judgeModel.isEmpty()) {
            log.info("The quiz question pool is off: no models are configured under {}generator-model and {}judge-model", PROPERTY_PREFIX, PROPERTY_PREFIX);
            return null;
        }
        String endpoint = baseUrl;
        String key = apiKey;
        String endpointProperty = PROPERTY_PREFIX + "base-url";
        if (endpoint.isEmpty()) {
            if (!OPENAI_CHAT_MODEL_PROVIDER.equals(chatModelProvider) || chatBaseUrl.isEmpty()) {
                log.info("The quiz question pool is off: neither {}base-url nor spring.ai.openai.base-url is configured", PROPERTY_PREFIX);
                return null;
            }
            if (chatMicrosoftFoundry || isCloudHostOf(chatBaseUrl)) {
                log.info("The quiz question pool is off: Hyperion's chat endpoint is a cloud provider; set {}base-url to a self-hosted endpoint to enable the pool",
                        PROPERTY_PREFIX);
                return null;
            }
            endpoint = chatBaseUrl;
            key = chatApiKey;
            endpointProperty = "spring.ai.openai.base-url";
        }
        try {
            validate(endpoint, endpointProperty, generatorModel, judgeModel, allowedModels);
        }
        catch (IllegalStateException e) {
            log.warn("The quiz question pool is off because its model configuration is invalid: {}", e.getMessage());
            return null;
        }
        Optional<List<String>> servedModels = servedModels(restClientBuilder, endpoint, key);
        if (servedModels.isPresent()) {
            List<String> missing = Stream.of(generatorModel, judgeModel).distinct().filter(model -> !servedModels.orElseThrow().contains(model)).toList();
            if (!missing.isEmpty()) {
                log.warn(
                        "The quiz question pool is off: the endpoint {} does not serve the model {}; set {}generator-model, {}judge-model and {}allowed-models to models it serves: {}",
                        endpoint, String.join(" and ", missing), PROPERTY_PREFIX, PROPERTY_PREFIX, PROPERTY_PREFIX, servedModels.orElseThrow());
                return null;
            }
        }
        OpenAiChatOptions options = OpenAiChatOptions.builder().baseUrl(endpoint).apiKey(key.isEmpty() ? NO_API_KEY : key).microsoftFoundry(false).gitHubModels(false)
                .timeout(timeout).model(generatorModel).build();
        log.info("The quiz question pool uses the endpoint {} (from {}) with the generator model {} and the judge model {}", endpoint, endpointProperty, generatorModel,
                judgeModel);
        return ChatClient.create(OpenAiChatModel.builder().options(options).build());
    }

    /**
     * Lists the models the endpoint serves, through the OpenAI-compatible {@code GET <base-url>/models}.
     *
     * @param restClientBuilder the builder of the client that sends the request
     * @param baseUrl           the endpoint, including its version prefix
     * @param apiKey            the key of the endpoint, empty when it needs none
     * @return the ids of the served models, or empty when the endpoint could not be asked or gave no model list, which is logged as a warning
     */
    static Optional<List<String>> servedModels(RestClient.Builder restClientBuilder, String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(MODEL_LIST_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(MODEL_LIST_READ_TIMEOUT);
        RestClient restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
        URI modelsUri = URI.create(baseUrl.replaceAll("/+$", "") + "/models");
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(modelsUri);
            if (!apiKey.isEmpty()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            ModelList modelList = request.retrieve().body(ModelList.class);
            if (modelList == null || modelList.data() == null) {
                log.warn("Could not check the models of the quiz question pool: {} returned no model list; the pool stays on", modelsUri);
                return Optional.empty();
            }
            return Optional.of(modelList.data().stream().filter(Objects::nonNull).map(Model::id).filter(Objects::nonNull).toList());
        }
        catch (RestClientException e) {
            log.warn("Could not check the models of the quiz question pool at {}: {}; the pool stays on", modelsUri, e.getMessage());
            return Optional.empty();
        }
    }

    /** The answer of {@code GET /models}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelList(@Nullable List<Model> data) {
    }

    /** One model of {@code GET /models}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Model(@Nullable String id) {
    }

    /**
     * Checks the pool's model configuration.
     *
     * @param baseUrl         the endpoint
     * @param baseUrlProperty the property the endpoint was read from, for the error message
     * @param generatorModel  the generator model, blank when none is configured
     * @param judgeModel      the judge model, blank when none is configured
     * @param allowedModels   the models the pool may use
     * @throws IllegalStateException if a model is missing or not listed in {@code allowedModels}, or the endpoint is not an http(s) URL or lies on a
     *                                   cloud provider
     */
    static void validate(String baseUrl, String baseUrlProperty, String generatorModel, String judgeModel, List<String> allowedModels) {
        List<String> missing = new ArrayList<>();
        if (generatorModel.isEmpty()) {
            missing.add(PROPERTY_PREFIX + "generator-model");
        }
        if (judgeModel.isEmpty()) {
            missing.add(PROPERTY_PREFIX + "judge-model");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The quiz question pool configuration is incomplete; set " + String.join(" and ", missing) + " or leave both empty");
        }
        String host = hostOf(baseUrl, baseUrlProperty);
        if (isCloudHost(host)) {
            throw new IllegalStateException(baseUrlProperty + " must point at a self-hosted endpoint, got the cloud provider host " + host);
        }
        List<String> allowed = allowedModels == null ? List.of() : allowedModels.stream().filter(Objects::nonNull).map(String::trim).filter(model -> !model.isEmpty()).toList();
        for (String model : List.of(generatorModel, judgeModel)) {
            if (!allowed.contains(model)) {
                throw new IllegalStateException("The quiz question pool model " + model + " is not listed in " + PROPERTY_PREFIX + "allowed-models " + allowed);
            }
        }
    }

    /** Whether the URL's host lies on a cloud provider; {@code false} for a URL without a host, which {@link #validate} rejects. */
    private static boolean isCloudHostOf(String baseUrl) {
        try {
            return isCloudHost(hostOf(baseUrl, ""));
        }
        catch (IllegalStateException _) {
            return false;
        }
    }

    private static String hostOf(String baseUrl, String baseUrlProperty) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(baseUrlProperty + " is not a valid URL: " + baseUrl, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalStateException(baseUrlProperty + " must be an http or https URL with a host, got " + baseUrl);
        }
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    static boolean isCloudHost(String host) {
        return CLOUD_HOSTS.stream().anyMatch(cloudHost -> host.equals(cloudHost) || host.endsWith("." + cloudHost));
    }

    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Returns whether the pool has a model endpoint and both models.
     *
     * @return whether builds can run
     */
    public boolean isConfigured() {
        return chatClient != null;
    }

    /**
     * Returns the client of the pool's own endpoint.
     *
     * @return the client
     * @throws ServiceUnavailableAlertException if the pool is not configured
     */
    public ChatClient chatClient() {
        if (chatClient == null) {
            throw notConfigured();
        }
        return chatClient;
    }

    /**
     * Returns the model that generates the pool's questions.
     *
     * @return the model name, empty when the pool is not configured
     */
    public String generatorModel() {
        return generatorModel;
    }

    /**
     * Returns the model that judges the pool's questions.
     *
     * @return the model name, empty when the pool is not configured
     */
    public String judgeModel() {
        return judgeModel;
    }

    /**
     * Creates the exception reported when a build is requested while the pool has no model endpoint.
     *
     * @return the exception
     */
    public static ServiceUnavailableAlertException notConfigured() {
        return new ServiceUnavailableAlertException("The quiz question pool has no local model configured", "QuizQuestionPool", "quizQuestionPoolModelNotConfigured");
    }
}
