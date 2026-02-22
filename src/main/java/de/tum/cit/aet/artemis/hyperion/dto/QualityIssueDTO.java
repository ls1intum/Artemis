package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QualityIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;

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
public record QualityIssueDTO(QualityIssueCategory category, Severity severity, String description, QualityIssueLocationDTO location, String suggestedFix,
        String impactOnLearners) {
}
