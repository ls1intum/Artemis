package de.tum.cit.aet.artemis.admin.domain;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.aiworker.api.LLMCostAmountDeserializer;

/**
 * Token usage and optional complete USD cost for a single LLM request.
 *
 * @param model                    LLM model name
 * @param numInputTokens           input tokens, including cached tokens
 * @param numOutputTokens          output tokens
 * @param pipelineId               operation that made the request
 * @param numCachedInputTokens     input tokens read from the prompt cache
 * @param numCacheWriteInputTokens input tokens written to the prompt cache
 * @param totalCostUsd             reported or estimated USD amount, or null if unknown
 */
@JsonIgnoreProperties({ "costPerMillionInputToken", "costPerMillionOutputToken", "costPerMillionCachedInputToken", "costPerMillionCacheWriteInputToken" })
public record LLMRequest(String model, int numInputTokens, int numOutputTokens, String pipelineId, int numCachedInputTokens, int numCacheWriteInputTokens,
        @Nullable @JsonDeserialize(using = LLMCostAmountDeserializer.class) Double totalCostUsd) {

    private static final Logger log = LoggerFactory.getLogger(LLMRequest.class);

    public LLMRequest {
        if (totalCostUsd != null && (!Double.isFinite(totalCostUsd) || totalCostUsd < 0)) {
            log.warn("Ignoring invalid USD cost for pipeline [{}]", pipelineId);
            totalCostUsd = null;
        }
    }

    /** Creates an unpriced request without prompt-cache usage. */
    public LLMRequest(String model, int numInputTokens, int numOutputTokens, String pipelineId) {
        this(model, numInputTokens, numOutputTokens, pipelineId, 0, 0, null);
    }
}
