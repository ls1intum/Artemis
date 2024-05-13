package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.course;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public record PyrisCourseChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages) {
}
