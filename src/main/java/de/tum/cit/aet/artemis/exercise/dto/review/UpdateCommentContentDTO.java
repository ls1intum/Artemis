package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCommentContentDTO(@NotNull CommentContentDTO content) {
}
