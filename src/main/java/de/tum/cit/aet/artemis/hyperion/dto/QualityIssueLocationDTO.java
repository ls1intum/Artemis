package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for quality issue location in the problem statement.
 *
 * @param startLine Starting line number (1-indexed)
 * @param endLine   Ending line number (1-indexed, inclusive)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Location of a quality issue within the problem statement")
public record QualityIssueLocationDTO(@Schema(description = "Starting line number (1-indexed)") Integer startLine,
        @Schema(description = "Ending line number (1-indexed, inclusive)") Integer endLine) {
}
