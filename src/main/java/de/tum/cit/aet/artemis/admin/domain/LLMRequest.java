package de.tum.cit.aet.artemis.admin.domain;

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
 */
public record LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId,
        String providerRequestId, Long numCachedInputTokens, float costPerMillionCachedInputToken, boolean costEstimateComplete, int numCacheWriteInputTokens,
        float costPerMillionCacheWriteInputToken) {

    public LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId) {
        this(model, numInputTokens, costPerMillionInputToken, numOutputTokens, costPerMillionOutputToken, pipelineId, null, null, 0f, true, 0, 0f);
    }

    /** Creates a request with explicit cache-read and cache-write prices. */
    public LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId,
            int numCachedInputTokens, float costPerMillionCachedInputToken, int numCacheWriteInputTokens, float costPerMillionCacheWriteInputToken) {
        this(model, numInputTokens, costPerMillionInputToken, numOutputTokens, costPerMillionOutputToken, pipelineId, null, (long) numCachedInputTokens,
                costPerMillionCachedInputToken, true, numCacheWriteInputTokens, costPerMillionCacheWriteInputToken);
    }

    /** Creates a provider-correlated request without cache-write usage. */
    public LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId,
            String providerRequestId, Long numCachedInputTokens, float costPerMillionCachedInputToken, boolean costEstimateComplete) {
        this(model, numInputTokens, costPerMillionInputToken, numOutputTokens, costPerMillionOutputToken, pipelineId, providerRequestId, numCachedInputTokens,
                costPerMillionCachedInputToken, costEstimateComplete, 0, 0f);
    }
}
