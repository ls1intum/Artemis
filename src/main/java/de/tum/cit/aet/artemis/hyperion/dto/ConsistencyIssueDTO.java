package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyIssueDTO(Severity severity, ConsistencyIssueCategory category, String description, String suggestedFix, List<ArtifactLocationDTO> locations) {
}
