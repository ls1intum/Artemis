package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStudentSubmissionDTO(Instant timestamp, double score) {
}
