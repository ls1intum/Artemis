package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyRecommendationDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * Websocket payload for Iris competency generation updates.
 *
 * @param runState current pipeline run state
 * @param error    optional error details
 * @param result   generated competency recommendations
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCompetencyGenerationStatusDTO(PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @Nullable List<PyrisCompetencyRecommendationDTO> result) {

    /**
     * Creates the websocket payload from the internal Pyris callback DTO without exposing token usage to the client.
     *
     * @param statusUpdate the internal Pyris status update
     * @return the client-facing websocket payload
     */
    public static IrisCompetencyGenerationStatusDTO of(PyrisCompetencyStatusUpdateDTO statusUpdate) {
        return new IrisCompetencyGenerationStatusDTO(statusUpdate.runState(), statusUpdate.error(), statusUpdate.result());
    }
}
