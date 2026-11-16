package de.tum.cit.aet.artemis.admin.domain;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.aiworker.api.LLMCostAmountDeserializer;
import de.tum.cit.aet.artemis.aiworker.api.LLMCostSourceDeserializer;

/**
 * This record is used for the LLMTokenUsageService to provide relevant information about LLM Token usage
 *
 * @param model                              LLM model (e.g. gpt-4o)
 * @param numInputTokens                     number of tokens of the LLM call, including the cached and cache-write tokens
 * @param costPerMillionInputToken           cost in Euro per million input tokens that were neither read from nor written to the prompt cache
 * @param numOutputTokens                    number of tokens of the LLM answer
 * @param costPerMillionOutputToken          cost in Euro per million output tokens
 * @param pipelineId                         String with the pipeline name (e.g. IRIS_COURSE_CHAT_PIPELINE)
 * @param numCachedInputTokens               number of input tokens the provider read from its prompt cache
 * @param costPerMillionCachedInputToken     cost in Euro per million input tokens read from the prompt cache
 * @param numCacheWriteInputTokens           number of input tokens the provider wrote to its prompt cache
 * @param costPerMillionCacheWriteInputToken cost in Euro per million input tokens written to the prompt cache
 * @param totalCostUsd                       optional complete USD amount for this request; old unit rates never establish its currency
 * @param costSource                         PROVIDER for an API amount, CONFIGURATION for a USD estimate; required with a USD amount
 */
public record LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId,
        int numCachedInputTokens, float costPerMillionCachedInputToken, int numCacheWriteInputTokens, float costPerMillionCacheWriteInputToken,
        @Nullable @JsonDeserialize(using = LLMCostAmountDeserializer.class) Double totalCostUsd,
        @Nullable @JsonDeserialize(using = LLMCostSourceDeserializer.class) String costSource) {

    private static final Logger log = LoggerFactory.getLogger(LLMRequest.class);

    public LLMRequest {
        if (totalCostUsd == null || !Double.isFinite(totalCostUsd) || totalCostUsd < 0 || !("PROVIDER".equals(costSource) || "CONFIGURATION".equals(costSource))) {
            if (totalCostUsd != null || costSource != null) {
                log.warn("Ignoring invalid or incomplete USD cost metadata for pipeline [{}]", pipelineId);
            }
            totalCostUsd = null;
            costSource = null;
        }
    }

    /** Creates a legacy request without an established USD amount. */
    public LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId,
            int numCachedInputTokens, float costPerMillionCachedInputToken, int numCacheWriteInputTokens, float costPerMillionCacheWriteInputToken) {
        this(model, numInputTokens, costPerMillionInputToken, numOutputTokens, costPerMillionOutputToken, pipelineId, numCachedInputTokens, costPerMillionCachedInputToken,
                numCacheWriteInputTokens, costPerMillionCacheWriteInputToken, null, null);
    }

    /**
     * Creates a request without prompt-cache usage.
     *
     * @param model                     LLM model (e.g. gpt-4o)
     * @param numInputTokens            number of tokens of the LLM call
     * @param costPerMillionInputToken  cost in Euro per million input tokens
     * @param numOutputTokens           number of tokens of the LLM answer
     * @param costPerMillionOutputToken cost in Euro per million output tokens
     * @param pipelineId                String with the pipeline name (e.g. IRIS_COURSE_CHAT_PIPELINE)
     */
    public LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId) {
        this(model, numInputTokens, costPerMillionInputToken, numOutputTokens, costPerMillionOutputToken, pipelineId, 0, 0f, 0, 0f);
    }
}
