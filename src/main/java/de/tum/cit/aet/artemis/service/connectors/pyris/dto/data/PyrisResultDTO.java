package de.tum.cit.aet.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisResultDTO(Instant completionDate, boolean successful, List<PyrisFeedbackDTO> feedbacks) {
}
