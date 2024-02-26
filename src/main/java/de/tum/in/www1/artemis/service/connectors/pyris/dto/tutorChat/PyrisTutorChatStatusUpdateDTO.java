package de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStatusUpdateDTO;

public class PyrisTutorChatStatusUpdateDTO extends PyrisStatusUpdateDTO {

    private final String result;

    public PyrisTutorChatStatusUpdateDTO(List<PyrisStageDTO> stages, String result) {
        super(stages);
        this.result = result;
    }

    public String getResult() {
        return result;
    }
}
