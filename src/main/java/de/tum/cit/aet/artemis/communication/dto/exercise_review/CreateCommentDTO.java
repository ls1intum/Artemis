package de.tum.cit.aet.artemis.communication.dto.exercise_review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.exercise_review.CommentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateCommentDTO(Long inReplyToId, @NotNull CommentType type, @NotNull CommentContentDTO content) {
}
