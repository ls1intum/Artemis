package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

public record PyrisLLMCostDTO(String modelInfo, int numInputTokens, float costPerInputToken, int numOutputTokens, float costPerOutputToken, String pipeline) {
}
