package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a quality issue found in the problem statement.
 *
 * @param category         Issue category: CLARITY, COHERENCE, or COMPLETENESS
 * @param severity         Issue severity: INFO, WARNING, or ERROR
 * @param description      Description of what is wrong
 * @param location         Location in the problem statement (line numbers)
 * @param suggestedFix     Concrete suggestion to fix the issue
 * @param impactOnLearners How this increases extraneous cognitive load
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QualityIssueDTO(String category, String severity, String description, QualityIssueLocationDTO location, String suggestedFix, String impactOnLearners) {
}
