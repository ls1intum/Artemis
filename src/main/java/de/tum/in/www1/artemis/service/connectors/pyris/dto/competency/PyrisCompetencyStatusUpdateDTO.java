package de.tum.in.www1.artemis.service.connectors.pyris.dto.competency;

import java.util.List;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for the Iris competency generation feature. Pyris sends callback updates back to Artemis during generation
 * of competencies, which are then forwarded to the user via Websockets.
 *
 * @param stages
 * @param competencies
 */
public record PyrisCompetencyStatusUpdateDTO(List<PyrisStageDTO> stages, List<Competency> competencies) {
}
