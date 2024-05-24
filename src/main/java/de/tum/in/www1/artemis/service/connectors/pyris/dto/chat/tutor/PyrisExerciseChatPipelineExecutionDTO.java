package de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.tutor;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatPipelineExecutionBaseDataDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisSubmissionDTO;

public record PyrisExerciseChatPipelineExecutionDTO(PyrisChatPipelineExecutionBaseDataDTO base, PyrisSubmissionDTO submission, PyrisProgrammingExerciseDTO exercise,
        PyrisCourseDTO course) {
}
