package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamInformationDTO(ZonedDateTime latestIndividualEndDate) {
}
