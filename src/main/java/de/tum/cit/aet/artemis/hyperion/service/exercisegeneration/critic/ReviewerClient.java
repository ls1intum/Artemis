package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

import com.knuddels.jtokkit.api.EncodingType;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;

/**
 * Bounded provider access shared by every reviewer pass: it renders the pass's system-prompt template, computes the output-token budget that still fits the configured context
 * window, applies the provider cooldown, and meters the response.
 * <p>
 * One owner for this policy means a new review pass cannot accidentally ship its own retry, budget, or metering behaviour, and a pass can be exercised against a scripted client
 * without going near provider configuration.
 */
final class ReviewerClient {

    /** Per-pass cap for visible output plus hidden reasoning. A review makes two baseline calls and at most one bounded oracle-correction call. */
    private static final int CRITIC_MAX_OUTPUT_TOKENS = 32_768;

    private static final int MIN_CRITIC_OUTPUT_TOKENS = 4_096;

    private static final int CRITIC_CONTEXT_SAFETY_TOKENS = 1_024;

    private static final JTokkitTokenCountEstimator TOKEN_ESTIMATOR = new JTokkitTokenCountEstimator(EncodingType.O200K_BASE);

    // Nullable like the sibling Hyperion services: the shared ChatClient bean is null when no AI provider is configured, in which case review fails closed.
    @Nullable
    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    @Nullable
    private final String configuredModel;

    private final Duration providerHardFailureCooldown;

    private final ProviderFailureCooldown providerFailureCooldown;

    private final int contextWindowTokens;

    private final boolean usesLegacyMaxTokens;

    @Nullable
    private final Integer configuredMaxOutputTokens;

    ReviewerClient(@Nullable ChatClient chatClient, HyperionPromptTemplateService templateService, @Nullable String configuredModel, Duration providerHardFailureCooldown,
            ProviderFailureCooldown providerFailureCooldown, int contextWindowTokens, @Nullable ChatOptions configuredOptions) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.configuredModel = configuredModel == null || configuredModel.isBlank() ? null : configuredModel;
        this.providerHardFailureCooldown = providerHardFailureCooldown;
        this.providerFailureCooldown = providerFailureCooldown;
        this.contextWindowTokens = contextWindowTokens;
        Integer maxCompletionTokens = configuredOptions instanceof OpenAiChatOptions openAiOptions ? openAiOptions.getMaxCompletionTokens() : null;
        this.usesLegacyMaxTokens = maxCompletionTokens == null && configuredOptions != null && configuredOptions.getMaxTokens() != null;
        this.configuredMaxOutputTokens = maxCompletionTokens != null ? maxCompletionTokens : configuredOptions == null ? null : configuredOptions.getMaxTokens();
    }

    /** Whether an AI reviewer is configured at all; every pass returns an explicit unavailable verdict rather than an empty one when it is not. */
    boolean configured() {
        return chatClient != null;
    }

    /** One output-capped, tool-free reviewer call; transport retry behavior is bounded by the configured OpenAI SDK client. */
    @Nullable
    String call(String systemPromptTemplate, String userPrompt, @Nullable Consumer<ChatResponse> usageSink) {
        return call(systemPromptTemplate, userPrompt, usageSink, CRITIC_MAX_OUTPUT_TOKENS);
    }

    /**
     * Renders the reviewer system prompt from its classpath template and issues one bounded call. Rendering happens here so every pass shares one loading path and the output-token
     * budget is computed from the prompt that is actually sent.
     */
    @Nullable
    String call(String systemPromptTemplate, String userPrompt, @Nullable Consumer<ChatResponse> usageSink, int maxOutputTokens) {
        String systemPrompt = templateService.render(systemPromptTemplate, Map.of());
        int outputTokens = reviewerOutputTokens(systemPrompt, userPrompt, maxOutputTokens);
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        if (usesLegacyMaxTokens) {
            options.maxTokens(outputTokens);
        }
        else {
            options.maxCompletionTokens(outputTokens);
        }
        if (configuredModel != null) {
            options.model(configuredModel);
        }
        // A thrown call yields no response to meter and the critic is advisory: its failure must never escalate into
        // stopping the whole generation job via the usage sink's uncertainty path.
        ChatResponse response = providerFailureCooldown.execute(ProviderFailureCooldown.keyForModel(configuredModel), providerHardFailureCooldown,
                () -> chatClient.prompt().system(systemPrompt).user(userPrompt).options(options).call().chatResponse());
        if (usageSink != null) {
            usageSink.accept(response);
        }
        return LLMTokenUsageService.extractResponseText(response);
    }

    private int reviewerOutputTokens(String systemPrompt, String userPrompt, int maxOutputTokens) {
        long promptTokens = (long) TOKEN_ESTIMATOR.estimate(systemPrompt) + TOKEN_ESTIMATOR.estimate(userPrompt);
        long available = (long) contextWindowTokens - promptTokens - CRITIC_CONTEXT_SAFETY_TOKENS;
        if (available < MIN_CRITIC_OUTPUT_TOKENS) {
            throw new IllegalArgumentException("The review prompt leaves insufficient context for a complete verdict.");
        }
        long providerLimit = configuredMaxOutputTokens == null || configuredMaxOutputTokens <= 0 ? Long.MAX_VALUE : configuredMaxOutputTokens;
        return (int) Math.min(Math.min(maxOutputTokens, available), providerLimit);
    }
}
