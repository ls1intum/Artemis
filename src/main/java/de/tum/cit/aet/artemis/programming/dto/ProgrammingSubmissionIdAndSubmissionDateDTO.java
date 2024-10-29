package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingSubmissionIdAndSubmissionDateDTO(long programmingSubmissionId, ZonedDateTime submissionDate) {
}
