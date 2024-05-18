package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;
import java.util.List;

public record PyrisResultDTO(Instant completionDate, boolean successful, List<PyrisFeedbackDTO> feedbacks) {
}
