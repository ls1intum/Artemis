package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Content for a user-provided review comment.")
public record UserCommentContentDTO(@Schema(description = "Comment text supplied by the reviewer.") @NotNull String text) implements CommentContentDTO {
}
