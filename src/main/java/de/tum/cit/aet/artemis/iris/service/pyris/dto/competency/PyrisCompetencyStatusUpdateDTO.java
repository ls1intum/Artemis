package de.tum.cit.aet.artemis.iris.service.pyris.dto.competency;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * DTO for the Iris competency generation feature.
 * Pyris sends callback updates back to Artemis during generation of competencies,
 * which Artemis maps to a client-facing websocket payload without token usage details.
 *
 * @param runState current pipeline run state
 * @param error    optional error details
 * @param result   List of competencies recommendations that have been generated so far
 * @param tokens   List of token usages send by Pyris for tracking the token usage and cost
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyStatusUpdateDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, List<PyrisCompetencyRecommendationDTO> result, List<LLMRequest> tokens) {
}
