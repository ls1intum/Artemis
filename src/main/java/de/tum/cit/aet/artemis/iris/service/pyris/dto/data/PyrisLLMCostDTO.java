package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLLMCostDTO(String modelInfo, int numInputTokens, float costPerInputToken, int numOutputTokens, float costPerOutputToken, String pipeline) {
}
