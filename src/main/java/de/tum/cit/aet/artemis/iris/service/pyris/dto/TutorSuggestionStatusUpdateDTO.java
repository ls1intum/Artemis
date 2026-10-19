package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/***
 * This class represents the status update of a tutor suggestion.
 *
 * @param artifact Generated tutor suggestion
 * @param result   Possible chat answer
 * @param runState current pipeline run state
 * @param error    optional error details
 * @param tokens   tokens used for the suggestion
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorSuggestionStatusUpdateDTO(String artifact, String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, List<LLMRequest> tokens) {
}
