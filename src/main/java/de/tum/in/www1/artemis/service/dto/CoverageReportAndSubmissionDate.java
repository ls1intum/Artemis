package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.hestia.CoverageReport;

public record CoverageReportAndSubmissionDate(CoverageReport coverageReport, ZonedDateTime submissionDate) {
}
