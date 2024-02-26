package de.tum.in.www1.artemis.web.rest.iris.dto;

import java.util.List;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public class IrisTutorChatStatusUpdateDTO extends IrisStatusUpdateDTO {

    private final IrisMessage result;

    public IrisTutorChatStatusUpdateDTO(List<PyrisStageDTO> stages, IrisMessage result) {
        super(stages);
        this.result = result;
    }

    public IrisMessage getResult() {
        return result;
    }
}
