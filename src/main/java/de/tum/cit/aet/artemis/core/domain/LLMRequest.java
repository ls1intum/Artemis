package de.tum.cit.aet.artemis.core.domain;

public record LLMRequest(String model, int numInputTokens, float costPerMillionInputToken, int numOutputTokens, float costPerMillionOutputToken, String pipelineId) {
}
