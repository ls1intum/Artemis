package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.lecture;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

// made onjects dto based instead of id based comparable to lecture flow
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureChatPipelineExecutionDTO(PyrisCourseDTO course, PyrisLectureDTO lecture, String sessionTitle, List<PyrisMessageDTO> chatHistory, PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, @Nullable String customInstructions) {
}
