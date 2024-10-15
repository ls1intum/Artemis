package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
// @formatter:off
public record PyrisResultDTO(
        Instant completionDate,
        boolean successful,
        List<PyrisFeedbackDTO> feedbacks
) {}
// @formatter:on
