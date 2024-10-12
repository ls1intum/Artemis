package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import de.tum.cit.aet.artemis.core.domain.LLMServiceType;

public record PyrisLLMCostDTO(String model_info, int num_input_tokens, float cost_per_input_token, int num_output_tokens, float cost_per_output_token, LLMServiceType pipeline) {
}
