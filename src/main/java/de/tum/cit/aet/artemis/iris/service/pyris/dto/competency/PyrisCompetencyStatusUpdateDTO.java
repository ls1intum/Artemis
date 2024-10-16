package de.tum.cit.aet.artemis.iris.service.pyris.dto.competency;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for the Iris competency generation feature.
 * Pyris sends callback updates back to Artemis during generation of competencies,
 * which are then forwarded to the user via Websockets.
 *
 * @param stages List of stages of the generation process
 * @param result List of competencies recommendations that have been generated so far
 */
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyStatusUpdateDTO(
        List<PyrisStageDTO> stages,
        List<PyrisCompetencyRecommendationDTO> result
) {}
// @formatter:on
