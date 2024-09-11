package de.tum.cit.aet.artemis.service.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.hestia.CoverageReport;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CoverageReportAndSubmissionDateDTO(CoverageReport coverageReport, ZonedDateTime submissionDate) {
}
