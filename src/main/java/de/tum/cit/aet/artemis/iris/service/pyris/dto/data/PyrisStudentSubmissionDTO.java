package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStudentSubmissionDTO(Instant timestamp, double score) {
}
