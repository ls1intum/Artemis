package de.tum.cit.aet.artemis.hyperion.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Provides the language models of the quiz question pool. The pool reaches its models only through its own OpenAI-compatible endpoint,
 * configured under {@code artemis.hyperion.quiz-pool} ({@code base-url}, {@code api-key}, {@code timeout}, {@code generator-model},
 * {@code judge-model}, {@code allowed-models}), and never through the chat model the rest of Artemis uses.
 * <p>
 * Without {@code base-url} and both model names the pool is not configured: builds are refused and no model is called. A partial
 * configuration, a model that is not listed in {@code allowed-models}, or an endpoint on a known cloud provider stops the server
 * at startup.
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

    /** Sent when the endpoint needs no key, so the client never falls back to a key from the environment. */
    private static final String NO_API_KEY = "none";

    @Nullable
    private final ChatClient chatClient;

    private final String generatorModel;

    private final String judgeModel;

    public HyperionQuizQuestionPoolModelService(@Value("${" + PROPERTY_PREFIX + "base-url:}") String baseUrl, @Value("${" + PROPERTY_PREFIX + "api-key:}") String apiKey,
            @Value("${" + PROPERTY_PREFIX + "timeout:5m}") Duration timeout, @Value("${" + PROPERTY_PREFIX + "generator-model:}") String generatorModel,
            @Value("${" + PROPERTY_PREFIX + "judge-model:}") String judgeModel, @Value("${" + PROPERTY_PREFIX + "allowed-models:}") List<String> allowedModels) {
        this.generatorModel = trim(generatorModel);
        this.judgeModel = trim(judgeModel);
        String endpoint = trim(baseUrl);
        if (!validate(endpoint, this.generatorModel, this.judgeModel, allowedModels)) {
            log.info("The quiz question pool is off: no model endpoint is configured under {}base-url", PROPERTY_PREFIX);
            this.chatClient = null;
            return;
        }
        OpenAiChatOptions options = OpenAiChatOptions.builder().baseUrl(endpoint).apiKey(trim(apiKey).isEmpty() ? NO_API_KEY : trim(apiKey)).microsoftFoundry(false)
                .gitHubModels(false).timeout(timeout).model(this.generatorModel).build();
        this.chatClient = ChatClient.create(OpenAiChatModel.builder().options(options).build());
        log.info("The quiz question pool uses the endpoint {} with the generator model {} and the judge model {}", endpoint, this.generatorModel, this.judgeModel);
    }

    /**
     * Checks the pool's model configuration.
     *
     * @param baseUrl        the endpoint, blank when none is configured
     * @param generatorModel the generator model, blank when none is configured
     * @param judgeModel     the judge model, blank when none is configured
     * @param allowedModels  the models the pool may use
     * @return whether the pool is configured; {@code false} when no part of the configuration is set
     * @throws IllegalStateException if the configuration is incomplete, names a model outside {@code allowedModels}, or points at a cloud provider
     */
    static boolean validate(String baseUrl, String generatorModel, String judgeModel, List<String> allowedModels) {
        if (baseUrl.isEmpty() && generatorModel.isEmpty() && judgeModel.isEmpty()) {
            return false;
        }
        List<String> missing = new ArrayList<>();
        if (baseUrl.isEmpty()) {
            missing.add(PROPERTY_PREFIX + "base-url");
        }
        if (generatorModel.isEmpty()) {
            missing.add(PROPERTY_PREFIX + "generator-model");
        }
        if (judgeModel.isEmpty()) {
            missing.add(PROPERTY_PREFIX + "judge-model");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The quiz question pool configuration is incomplete; set " + String.join(", ", missing) + " or leave all of them empty");
        }
        String host = hostOf(baseUrl);
        if (isCloudHost(host)) {
            throw new IllegalStateException(PROPERTY_PREFIX + "base-url must point at a self-hosted endpoint, got the cloud provider host " + host);
        }
        List<String> allowed = allowedModels == null ? List.of() : allowedModels.stream().filter(Objects::nonNull).map(String::trim).filter(model -> !model.isEmpty()).toList();
        for (String model : List.of(generatorModel, judgeModel)) {
            if (!allowed.contains(model)) {
                throw new IllegalStateException("The quiz question pool model " + model + " is not listed in " + PROPERTY_PREFIX + "allowed-models " + allowed);
            }
        }
        return true;
    }

    private static String hostOf(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(PROPERTY_PREFIX + "base-url is not a valid URL: " + baseUrl, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalStateException(PROPERTY_PREFIX + "base-url must be an http or https URL with a host, got " + baseUrl);
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
        return new ServiceUnavailableAlertException("The quiz question pool has no local model configured", "QuizQuestionPool", "modelNotConfigured");
    }
}
