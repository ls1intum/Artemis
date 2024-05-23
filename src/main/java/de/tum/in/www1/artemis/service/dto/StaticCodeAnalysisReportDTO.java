package de.tum.in.www1.artemis.service.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;

/**
 * Record to represent the data transfer object for static code analysis reports.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool tool, List<StaticCodeAnalysisIssue> issues) implements Serializable {
}
