package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.exercise;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisMessageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisExerciseChatPipelineExecutionDTO(PyrisSubmissionDTO submission, PyrisProgrammingExerciseDTO exercise, PyrisCourseDTO course, List<PyrisMessageDTO> chatHistory,
        PyrisUserDTO user, PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {

}
