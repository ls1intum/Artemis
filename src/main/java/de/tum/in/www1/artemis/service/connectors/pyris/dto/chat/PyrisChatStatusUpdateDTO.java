package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages, List<String> suggestions) {
}
