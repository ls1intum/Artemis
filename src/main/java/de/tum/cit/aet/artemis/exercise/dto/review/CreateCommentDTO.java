package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateCommentDTO(@NotNull CommentType type, @NotNull CommentContentDTO content) {
}
