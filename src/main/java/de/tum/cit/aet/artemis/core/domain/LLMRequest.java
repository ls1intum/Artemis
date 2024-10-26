package de.tum.cit.aet.artemis.core.domain;

/**
 * This record is used for the LLMTokenUsageService to provide relevant information about LLM Token usage
 *
 * @param model                     LLM model (e.g. gpt-4o)
 * @param numInputTokens            number of tokens of the LLM call
 * @param costPerMillionInputToken  cost in Euro per million input tokens
 * @param numOutputTokens           number of tokens of the LLM answer
 * @param costPerMillionOutputToken cost in Euro per million output tokens
 * @param pipelineId                String with the pipeline name (e.g. IRIS_COURSE_CHAT_PIPELINE)
 */
public record LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId) {
}
