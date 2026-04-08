package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QualityIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for a quality issue found in the problem statement.
 *
 * @param category         Issue category: CLARITY, COHERENCE, or COMPLETENESS
 * @param severity         Issue severity: LOW, MEDIUM, or HIGH
 * @param description      Description of what is wrong
 * @param location         Location in the problem statement (line numbers)
 * @param suggestedFix     Concrete suggestion to fix the issue
 * @param impactOnLearners How this increases extraneous cognitive load
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A quality issue found in the problem statement")
public record QualityIssueDTO(@Schema(description = "Issue category: CLARITY, COHERENCE, or COMPLETENESS") QualityIssueCategory category,
        @Schema(description = "Issue severity: LOW, MEDIUM, or HIGH") Severity severity, @Schema(description = "Description of what is wrong") String description,
        @Schema(description = "Location in the problem statement") QualityIssueLocationDTO location,
        @Schema(description = "Concrete suggestion to fix the issue") String suggestedFix,
        @Schema(description = "How this issue increases extraneous cognitive load") String impactOnLearners) {
}
