package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import de.tum.cit.aet.artemis.core.domain.LLMServiceType;

public record PyrisLLMCostDTO(String modelInfo, int numInputTokens, float costPerInputToken, int numOutputTokens, float costPerOutputToken, LLMServiceType pipeline) {
}
