package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import de.tum.cit.aet.artemis.core.domain.LLMServiceType;

public record PyrisLLMCostDTO(String model_info, int num_input_tokens, int num_output_tokens, LLMServiceType pipeline) {
}
