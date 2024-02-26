package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.LocalDateTime;
import java.util.List;

public record PyrisResultDTO(LocalDateTime completionDate, boolean successful, List<PyrisFeedbackDTO> feedbacks) {
}
