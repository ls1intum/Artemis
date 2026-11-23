package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

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
import de.tum.cit.aet.artemis.aiworker.api.LLMResponseCost;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/** Records strict provider accounting for generation without changing shared LLM pricing or usage rules. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationTokenUsageService {

    /** Token counts, provider request identifier and known USD subtotal for one generation call. */
    public record GenerationUsage(String model, int numInputTokens, int numOutputTokens, String pipelineId, @Nullable String providerRequestId, @Nullable Long numCachedInputTokens,
            boolean costUsdComplete, double costUsd) {
    }

    private static final Logger log = LoggerFactory.getLogger(GenerationTokenUsageService.class);

    private final LLMTokenUsageService tokenUsageService;

    private final LlmProperties llmProperties;

    public GenerationTokenUsageService(LLMTokenUsageService tokenUsageService, LlmProperties llmProperties) {
        this.tokenUsageService = tokenUsageService;
        this.llmProperties = llmProperties;
    }

    /**
     * Builds usage with provider correlation and cache pricing without changing the shared persistence schema.
     *
     * @param preset            the preset that made the call, whose endpoint price list prices it; null leaves the estimate incomplete
     * @param model             model identifier
     * @param inputTokens       number of input tokens
     * @param outputTokens      number of output tokens
     * @param pipelineId        pipeline identifier
     * @param providerRequestId provider response identifier, when available
     * @param cachedInputTokens cache-read input tokens, when reported
     * @return request with configured costs, cache usage and transient provider metadata
     */
    public GenerationUsage buildLLMRequest(@Nullable String preset, String model, int inputTokens, int outputTokens, String pipelineId, @Nullable String providerRequestId,
            @Nullable Long cachedInputTokens) {
        ModelCost cost = modelCost(preset);
        if (cost.equals(ModelCost.UNKNOWN) && inputTokens + outputTokens > 0) {
            log.warn("No fallback price for model '{}' on pipeline [{}] (preset {}); recording an incomplete estimate. Add the model to the price list of the preset's endpoint",
                    model, pipelineId, preset);
        }
        boolean zeroTokenRequest = (long) inputTokens + outputTokens == 0;
        boolean cachePriceResolved = cachedInputTokens != null ? cachedInputTokens == 0 || cost.cachedInputUsd() != null
                : cost.inputUsd() != null && cost.cachedInputUsd() != null && cost.inputUsd().equals(cost.cachedInputUsd());
        long uncachedTokens = cachedInputTokens == null ? inputTokens : inputTokens - cachedInputTokens;
        boolean complete = zeroTokenRequest
                || (uncachedTokens == 0 || cost.inputUsd() != null) && (inputTokens == 0 || cachePriceResolved) && (outputTokens == 0 || cost.outputUsd() != null);
        double inputCost;
        if (cachedInputTokens == null) {
            // With no split, only the cheaper configured input rate is a safe lower bound. An unknown rate contributes zero.
            inputCost = inputTokens * Math.min(orZero(cost.inputUsd()), orZero(cost.cachedInputUsd()));
        }
        else {
            inputCost = uncachedTokens * orZero(cost.inputUsd()) + cachedInputTokens * orZero(cost.cachedInputUsd());
        }
        double costUsd = (inputCost + outputTokens * orZero(cost.outputUsd())) / 1_000_000.0;
        return new GenerationUsage(model, inputTokens, outputTokens, pipelineId, providerRequestId, cachedInputTokens, complete, costUsd);
    }

    private ModelCost modelCost(@Nullable String presetName) {
        LlmProperties.Preset preset = presetName == null ? null : llmProperties.preset(presetName);
        LlmProperties.Price price = preset == null ? null : llmProperties.priceOf(preset);
        return price == null ? ModelCost.UNKNOWN : new ModelCost(price.input(), price.output(), price.cachedInput());
    }

    private static double orZero(@Nullable Double value) {
        return value == null ? 0d : value;
    }

    private record ModelCost(@Nullable Double inputUsd, @Nullable Double outputUsd, @Nullable Double cachedInputUsd) {

        static final ModelCost UNKNOWN = new ModelCost(null, null, null);
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
            String model = Objects.toString(metadata.getModel(), "");
            Double reportedCost = LLMResponseCost.reportedUsd(usage.getNativeUsage(), promptTokens, completionTokens);
            GenerationUsage request = reportedCost != null
                    ? new GenerationUsage(model, promptTokens, completionTokens, pipelineId, metadata.getId(), cachedInputTokens, true, reportedCost)
                    : buildLLMRequest(LlmClientService.presetOf(metadata), model, promptTokens, completionTokens, pipelineId, metadata.getId(), cachedInputTokens);
            LLMRequest persisted = new LLMRequest(model, promptTokens, completionTokens, pipelineId, cachedInputTokens == null ? 0 : cachedInputTokens.intValue(), 0,
                    request.costUsdComplete() ? request.costUsd() : null);
            tokenUsageService.saveLLMTokenUsage(List.of(persisted), serviceType, builderFunction);
            recordedUsageSink.accept(request);
            return true;
        }
        catch (Exception e) {
            log.warn("Failed to store token usage for pipeline [{}]: {}", pipelineId, e.getMessage(), e);
            return false;
        }
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
        long promptTokens = Objects.requireNonNullElse(usage.getPromptTokens(), 0).longValue();
        long completionTokens = Objects.requireNonNullElse(usage.getCompletionTokens(), 0).longValue();
        Long reportedCached = usage.getCacheReadInputTokens();
        // A provider that reports no split is treated as having served nothing from cache, so an unknown split can never understate the spend.
        long cached = reportedCached == null ? 0 : Math.max(0, Math.min(promptTokens, reportedCached));
        double weight = Math.clamp(cachedInputTokenWeight, 0d, 1d);
        return Math.max(0, Math.round((promptTokens - cached) + cached * weight) + completionTokens);
    }
}
