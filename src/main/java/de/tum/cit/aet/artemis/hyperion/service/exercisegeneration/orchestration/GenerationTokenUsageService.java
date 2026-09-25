package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService.LLMTokenUsageBuilder;
import de.tum.cit.aet.artemis.hyperion.config.GenerationModelCostConfiguration;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/** Records strict provider accounting for generation without changing shared LLM pricing or usage rules. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationTokenUsageService {

    /** Transient provider and cache accounting; the shared usage ledger keeps its existing contract. */
    public record GenerationUsage(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId,
            @Nullable String providerRequestId, @Nullable Long numCachedInputTokens, float costPerMillionCachedInputToken, boolean costEstimateComplete) {
    }

    private static final Logger log = LoggerFactory.getLogger(GenerationTokenUsageService.class);

    private static final Pattern DATE_SUFFIX_PATTERN = Pattern.compile("-?\\d{4}-\\d{2}-\\d{2}$");

    private final LLMTokenUsageService tokenUsageService;

    private final Map<String, ModelCost> costs;

    private final Map<String, ModelCost> costsByStrippedKey;

    public GenerationTokenUsageService(LLMTokenUsageService tokenUsageService, GenerationModelCostConfiguration costConfiguration) {
        this.tokenUsageService = tokenUsageService;
        this.costs = costConfiguration.getModelCosts().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> new ModelCost(e.getValue().getInputCostPerMillionEur(), e.getValue().getOutputCostPerMillionEur(), e.getValue().getCachedInputCostPerMillionEur())));
        this.costsByStrippedKey = costConfiguration.getModelCosts().entrySet().stream()
                .collect(Collectors.toMap(entry -> GenerationModelCostConfiguration.stripToAlphanumeric(entry.getKey()),
                        entry -> new StrippedModelCost(entry.getKey(), GenerationModelCostConfiguration.stripToAlphanumeric(entry.getKey()),
                                new ModelCost(entry.getValue().getInputCostPerMillionEur(), entry.getValue().getOutputCostPerMillionEur(),
                                        entry.getValue().getCachedInputCostPerMillionEur())),
                        GenerationTokenUsageService::throwOnStrippedCostCollision))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().cost()));
    }

    /**
     * Build an GenerationUsage with automatic cost lookup from configuration.
     *
     * @param model        model identifier
     * @param inputTokens  number of input tokens
     * @param outputTokens number of output tokens
     * @param pipelineId   pipeline identifier
     * @return GenerationUsage with costs from configuration
     */
    public GenerationUsage buildLLMRequest(String model, int inputTokens, int outputTokens, String pipelineId) {
        return buildLLMRequest(model, inputTokens, outputTokens, pipelineId, null, null);
    }

    /**
     * Builds an LLM request with provider correlation and cache usage kept in memory while preserving the existing persistence schema.
     *
     * @param model             model identifier
     * @param inputTokens       number of input tokens
     * @param outputTokens      number of output tokens
     * @param pipelineId        pipeline identifier
     * @param providerRequestId provider response identifier, when available
     * @param cachedInputTokens cache-read input tokens, when reported
     * @return request with configured costs and transient provider metadata
     */
    public GenerationUsage buildLLMRequest(String model, int inputTokens, int outputTokens, String pipelineId, @Nullable String providerRequestId,
            @Nullable Long cachedInputTokens) {
        String normalized = model != null ? DATE_SUFFIX_PATTERN.matcher(model).replaceAll("") : "";
        String stripped = GenerationModelCostConfiguration.stripToAlphanumeric(normalized);
        ModelCost cost = costs.getOrDefault(normalized, costsByStrippedKey.getOrDefault(stripped, ModelCost.UNKNOWN));
        if (cost.equals(ModelCost.UNKNOWN) && inputTokens + outputTokens > 0) {
            log.warn("No LLM cost configured for model '{}' (normalized '{}', stripped '{}') on pipeline [{}]; recording an incomplete zero estimate. Known cost keys: {}", model,
                    normalized, stripped, pipelineId, costs.keySet());
        }
        boolean zeroTokenRequest = inputTokens + outputTokens == 0;
        boolean cachePriceResolved = cachedInputTokens != null ? cachedInputTokens == 0 || cost.cachedInput() != null
                : cost.input() != null && cost.cachedInput() != null && cost.input().equals(cost.cachedInput());
        boolean complete = zeroTokenRequest || (inputTokens == 0 || cost.input() != null && cachePriceResolved) && (outputTokens == 0 || cost.output() != null);
        return new GenerationUsage(model, inputTokens, cost.inputOrZero(), outputTokens, cost.outputOrZero(), pipelineId, providerRequestId, cachedInputTokens,
                cost.cachedInputOrZero(), complete);
    }

    private record ModelCost(@Nullable Float input, @Nullable Float output, @Nullable Float cachedInput) {

        static final ModelCost UNKNOWN = new ModelCost(null, null, null);

        float inputOrZero() {
            return input == null ? 0f : input;
        }

        float outputOrZero() {
            return output == null ? 0f : output;
        }

        float cachedInputOrZero() {
            return cachedInput == null ? 0f : cachedInput;
        }
    }

    private record StrippedModelCost(String originalKey, String strippedKey, ModelCost cost) {
    }

    private static StrippedModelCost throwOnStrippedCostCollision(StrippedModelCost existing, StrippedModelCost replacement) {
        String message = new StringBuilder("Conflicting LLM model cost keys '").append(existing.originalKey()).append("' and '").append(replacement.originalKey())
                .append("' normalize to identical stripped key '").append(existing.strippedKey()).append("'").toString();
        throw new IllegalStateException(message);
    }

    private static float effectiveInputCost(GenerationUsage request) {
        if (request.numCachedInputTokens() == null || request.numInputTokens() == 0) {
            return request.costPerMillionInputToken();
        }
        long cachedTokens = Math.min(request.numInputTokens(), request.numCachedInputTokens());
        long uncachedTokens = request.numInputTokens() - cachedTokens;
        return (uncachedTokens * request.costPerMillionInputToken() + cachedTokens * request.costPerMillionCachedInputToken()) / request.numInputTokens();
    }

    /**
     * Persists known provider token counts and publishes the richer, transient generation accounting record.
     * Missing counts fail accounting; missing prices remain incomplete estimates, not known-free usage.
     *
     * @param chatResponse      provider response
     * @param serviceType       usage owner
     * @param pipelineId        usage pipeline
     * @param builderFunction   persisted trace context
     * @param recordedUsageSink generation accounting observer
     * @return whether token usage was stored and observed
     */
    public boolean trackChatResponseTokenUsage(@Nullable ChatResponse chatResponse, LLMServiceType serviceType, String pipelineId,
            Function<LLMTokenUsageBuilder, LLMTokenUsageBuilder> builderFunction, Consumer<GenerationUsage> recordedUsageSink) {
        try {
            if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
                return false;
            }
            ChatResponseMetadata metadata = chatResponse.getMetadata();
            Usage usage = metadata.getUsage();
            if (usage instanceof org.springframework.ai.chat.metadata.EmptyUsage) {
                return false;
            }
            Integer promptTokens = usage.getPromptTokens();
            Integer completionTokens = usage.getCompletionTokens();
            Long cachedInputTokens = usage.getCacheReadInputTokens();
            if (promptTokens == null || completionTokens == null || promptTokens < 0 || completionTokens < 0
                    || cachedInputTokens != null && (cachedInputTokens < 0 || cachedInputTokens > promptTokens)) {
                return false;
            }
            String model = metadata.getModel() != null ? metadata.getModel() : "";
            GenerationUsage llmRequest = buildLLMRequest(model, promptTokens, completionTokens, pipelineId, metadata.getId(), cachedInputTokens);
            LLMRequest persisted = new LLMRequest(model, promptTokens, effectiveInputCost(llmRequest), completionTokens, llmRequest.costPerMillionOutputToken(), pipelineId);
            tokenUsageService.saveLLMTokenUsage(List.of(persisted), serviceType, builderFunction);
            recordedUsageSink.accept(llmRequest);
            return true;
        }
        catch (Exception e) {
            log.warn("Failed to store token usage for pipeline [{}]: {}", pipelineId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * @param request generation usage, including cache pricing
     * @return estimated EUR; incomplete prices remain marked on the request
     */
    public static double estimatedCostEur(GenerationUsage request) {
        long cachedTokens = request.numCachedInputTokens() == null ? 0 : request.numCachedInputTokens();
        long uncachedTokens = request.numInputTokens() - cachedTokens;
        return (uncachedTokens * request.costPerMillionInputToken() + cachedTokens * request.costPerMillionCachedInputToken()
                + request.numOutputTokens() * request.costPerMillionOutputToken()) / 1_000_000.0;
    }

    /**
     * @param chatResponse           provider response
     * @param cachedInputTokenWeight budget weight of cache hits
     * @return tokens charged to the generation budget
     */
    public static long billableTokens(@Nullable ChatResponse chatResponse, double cachedInputTokenWeight) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return 0;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        long promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens().longValue();
        long completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens().longValue();
        Long reportedCached = usage.getCacheReadInputTokens();
        // A provider that reports no split is treated as having served nothing from cache, so an unknown split can never understate the spend.
        long cached = reportedCached == null ? 0 : Math.max(0, Math.min(promptTokens, reportedCached));
        double weight = Math.clamp(cachedInputTokenWeight, 0d, 1d);
        return Math.max(0, Math.round((promptTokens - cached) + cached * weight) + completionTokens);
    }
}
