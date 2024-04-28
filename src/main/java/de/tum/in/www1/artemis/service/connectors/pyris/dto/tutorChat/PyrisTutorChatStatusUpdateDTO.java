package de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public record PyrisTutorChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages) {
}
