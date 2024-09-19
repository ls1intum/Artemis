package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.text_exercise;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisTextExerciseDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTextExerciseChatPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, PyrisTextExerciseDTO exercise, String currentAnswer) {
}
