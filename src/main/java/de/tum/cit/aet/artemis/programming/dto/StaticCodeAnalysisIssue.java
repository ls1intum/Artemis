package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Record to represent issues found in static code analysis
 *
 * @param filePath    Path to source file containing the error, uses UNIX file separators
 * @param startLine   Start line of the issue in the source file
 * @param endLine     End line of the issue in the source file
 * @param startColumn Start column of the issue in the source file
 * @param endColumn   End column of the issue in the source file
 * @param rule        Rule name associated with the issue
 * @param category    Category of the issue
 * @param message     Detailed message describing the issue
 * @param priority    Priority level of the issue
 * @param penalty     Penalty points for the issue, if applicable
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StaticCodeAnalysisIssue(String filePath, Integer startLine, Integer endLine, Integer startColumn, Integer endColumn, String rule, String category, String message,
        String priority, Double penalty) implements Serializable {
}
