package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.exercise;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatPipelineExecutionBaseDataDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisSubmissionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisExerciseChatPipelineExecutionDTO(PyrisChatPipelineExecutionBaseDataDTO base, PyrisSubmissionDTO submission, PyrisProgrammingExerciseDTO exercise,
        PyrisCourseDTO course) {
}
