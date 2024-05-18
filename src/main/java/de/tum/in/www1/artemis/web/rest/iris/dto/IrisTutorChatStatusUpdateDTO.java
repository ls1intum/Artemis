package de.tum.in.www1.artemis.web.rest.iris.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public record IrisTutorChatStatusUpdateDTO(IrisMessage result, List<PyrisStageDTO> stages) {

}
