package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.tutorsuggestion;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Represents the execution of a pipeline for a tutor suggestion chat session.
 *
 * @param courseDTO     courseDTO of the course post is in
 * @param exerciseId    exerciseId of the exercise the post might related to
 * @param post          post for that the suggestion is made
 * @param chatHistory   chat history of the session
 * @param user          user that is asking for the suggestion
 * @param settings      pipeline settings
 * @param initialStages initial stages of the pipeline
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTutorSuggestionPipelineExecutionDTO(Optional<PyrisCourseDTO> courseDTO, Optional<Integer> exerciseId, PyrisPostDTO post, List<PyrisMessageDTO> chatHistory,
        PyrisUserDTO user, PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages) {
}
