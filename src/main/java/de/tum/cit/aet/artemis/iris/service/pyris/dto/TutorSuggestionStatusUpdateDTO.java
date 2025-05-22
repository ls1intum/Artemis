package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/***
 * This class represents the status update of a tutor suggestion.
 *
 * @param artifact Generated tutor suggestion
 * @param result   Possible chat answer
 * @param stages   Stages of the pipeline
 * @param tokens   tokens used for the suggestion
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorSuggestionStatusUpdateDTO(String artifact, String result, List<PyrisStageDTO> stages, List<LLMRequest> tokens) {
}
