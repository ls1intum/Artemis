package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

import java.util.List;

public record PyrisStatusUpdateDTO(String result, List<PyrisStageDTO> stages) {
}
