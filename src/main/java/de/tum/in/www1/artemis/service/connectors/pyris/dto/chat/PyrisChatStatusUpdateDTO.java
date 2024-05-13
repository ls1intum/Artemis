package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public record PyrisChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages) {
}
