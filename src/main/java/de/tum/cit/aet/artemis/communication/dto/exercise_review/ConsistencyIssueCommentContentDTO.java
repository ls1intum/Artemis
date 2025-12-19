package de.tum.cit.aet.artemis.communication.dto.exercise_review;

import de.tum.cit.aet.artemis.hyperion.domain.ConsistencyIssueCategory;
import de.tum.cit.aet.artemis.hyperion.domain.Severity;

public record ConsistencyIssueCommentContentDTO(Severity severity, ConsistencyIssueCategory category, String description, String suggestedFix) implements CommentContentDTO {
}
