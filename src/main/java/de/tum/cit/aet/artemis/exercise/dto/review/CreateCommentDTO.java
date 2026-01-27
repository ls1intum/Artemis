package de.tum.cit.aet.artemis.exercise.dto.review;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request payload to create a new comment.")
public record CreateCommentDTO(@Schema(description = "Type of the comment.") @NotNull CommentType type,
        @Schema(description = "Polymorphic content payload of the comment.") @NotNull CommentContentDTO content) {

    /**
     * Convert this DTO into a new comment entity.
     *
     * @return the initialized comment entity
     */
    public Comment toEntity() {
        Comment comment = new Comment();
        comment.setType(type);
        comment.setContent(content);
        return comment;
    }
}
