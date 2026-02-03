package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for quality issue location in the problem statement.
 *
 * @param startLine Starting line number (1-indexed)
 * @param endLine   Ending line number (1-indexed, inclusive)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QualityIssueLocationDTO(Integer startLine, Integer endLine) {
}
