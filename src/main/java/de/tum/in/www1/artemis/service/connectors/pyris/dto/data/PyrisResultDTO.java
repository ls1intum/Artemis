package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.ZonedDateTime;
import java.util.List;

public record PyrisResultDTO(ZonedDateTime completionDate, boolean successful, List<PyrisFeedbackDTO> feedbacks) {
}
