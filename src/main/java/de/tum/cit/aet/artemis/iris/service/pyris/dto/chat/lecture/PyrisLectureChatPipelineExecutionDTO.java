package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureChatPipelineExecutionDTO(long courseId, long lectureId, String sessionTitle, List<PyrisMessageDTO> chatHistory, PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, @Nullable String customInstructions) {
}
