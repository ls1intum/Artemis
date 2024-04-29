package de.tum.in.www1.artemis.service.connectors.pyris.dto.courseChat;

import java.util.List;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.*;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

public final class PyrisCourseChatPipelineExecutionDTO extends PyrisPipelineExecutionDTO {

    private final PyrisCourseDTO course;

    private final List<PyrisMessageDTO> chatHistory;

    private final PyrisUserDTO user;

    public PyrisCourseChatPipelineExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, PyrisCourseDTO course,
            List<PyrisMessageDTO> chatHistory, PyrisUserDTO user) {
        super(settings, initialStages);
        this.course = course;
        this.chatHistory = chatHistory;
        this.user = user;
    }

    public PyrisCourseDTO getCourse() {
        return course;
    }

    public List<PyrisMessageDTO> getChatHistory() {
        return chatHistory;
    }

    public PyrisUserDTO getUser() {
        return user;
    }
}
