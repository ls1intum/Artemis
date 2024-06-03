package de.tum.in.www1.artemis.web.rest.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisTutorChatStatusUpdateDTO(IrisMessage result, List<PyrisStageDTO> stages) {

}
