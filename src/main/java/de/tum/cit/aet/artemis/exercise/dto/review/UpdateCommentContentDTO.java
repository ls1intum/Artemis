package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request payload to update comment content.")
public record UpdateCommentContentDTO(@Schema(description = "Updated polymorphic content payload of the comment.") @NotNull CommentContentDTO content) {
}
