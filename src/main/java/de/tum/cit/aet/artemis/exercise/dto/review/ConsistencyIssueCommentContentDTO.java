package de.tum.cit.aet.artemis.exercise.dto.review;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConsistencyIssueCommentContentDTO(Severity severity, ConsistencyIssueCategory category, String description, String suggestedFix) implements CommentContentDTO {
}
