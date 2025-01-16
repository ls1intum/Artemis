package de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for the Iris rewriting feature.
 * Pyris sends callback updates back to Artemis during rewriting of the text. These updates contain the current status of the rewriting process,
 * which are then forwarded to the user via Websockets.
 *
 * @param stages List of stages of the generation process
 * @param result The result of the rewriting process so far
 * @param tokens List of token usages send by Pyris for tracking the token usage and cost
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisRewritingStatusUpdateDTO(List<PyrisStageDTO> stages, String result, List<LLMRequest> tokens) {
}
