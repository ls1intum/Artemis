package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;

/**
 * DTO for a consistency issue.
 *
 * @param severity     The severity of the issue
 * @param category     The category of the issue
 * @param description  The description of the issue
 * @param suggestedFix The suggested fix for the issue
 * @param locations    The locations where the issue was found
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyIssueDTO(Severity severity, ConsistencyIssueCategory category, String description, String suggestedFix, List<ArtifactLocationDTO> locations) {
}
