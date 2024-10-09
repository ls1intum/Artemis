package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTextExerciseChatPipelineExecutionDTO(PyrisPipelineExecutionDTO execution, PyrisTextExerciseDTO exercise, List<PyrisMessageDTO> conversation,
                                                        String currentSubmission) {
}
