package de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTutorChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages) {
}
