package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;

/**
 * Record to represent the data transfer object for static code analysis reports.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool tool, List<StaticCodeAnalysisIssue> issues) implements Serializable {
}
