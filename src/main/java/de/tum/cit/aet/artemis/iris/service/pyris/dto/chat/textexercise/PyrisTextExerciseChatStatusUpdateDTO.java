package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.textexercise;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * A response from Pyris in the text exercise chat pipeline.
 * The text exercise chat pipeline does not use {@code PyrisChatStatusUpdateDTO} because of the lack of suggestions.
 *
 * @param result The chat response from Iris, if this is the final update
 * @param stages The status of the pipeline execution described in a list of stages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTextExerciseChatStatusUpdateDTO(String result, List<PyrisStageDTO> stages) {
}
