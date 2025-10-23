package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.tutorsuggestion;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Represents the execution of a pipeline for a tutor suggestion chat session.
 *
 * @param course                 courseDTO of the course post is in
 * @param post                   post for that the suggestion is made
 * @param chatHistory            chat history of the session
 * @param user                   user that is asking for the suggestion
 * @param settings               pipeline settings
 * @param initialStages          initial stages of the pipeline
 * @param textExerciseDTO        optional text exercise DTO
 * @param submission             optional submission DTO
 * @param programmingExerciseDTO optional programming exercise DTO
 * @param lectureId              optional lecture ID
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTutorSuggestionPipelineExecutionDTO(PyrisCourseDTO course, PyrisPostDTO post, List<PyrisMessageDTO> chatHistory, PyrisUserDTO user,
        PyrisPipelineExecutionSettingsDTO settings, List<PyrisStageDTO> initialStages, Optional<PyrisTextExerciseDTO> textExerciseDTO, Optional<PyrisSubmissionDTO> submission,
        Optional<PyrisProgrammingExerciseDTO> programmingExerciseDTO, Optional<Long> lectureId) {
}
