package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages, List<String> suggestions, List<LLMRequest> tokens) {
}
