package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.service.connectors.pyris.domain.status.PyrisStageState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStageDTO(String name, int weight, PyrisStageState state, String message) {
}
