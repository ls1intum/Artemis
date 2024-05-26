package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.course;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages) {
}
