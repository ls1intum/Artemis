package de.tum.cit.aet.artemis.communication.dto.exercise_review;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.exercise_review.Comment;
import de.tum.cit.aet.artemis.communication.domain.exercise_review.CommentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommentDTO(Long id, Long threadId, Long authorId, Long inReplyToId, CommentType type, CommentContentDTO content, Instant createdDate, Instant lastModifiedDate) {

    /**
     * Maps a Comment entity to a DTO.
     *
     * @param comment the comment entity
     */
    public CommentDTO(Comment comment) {
        this(comment.getId(), comment.getThread() != null ? comment.getThread().getId() : null, comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                comment.getInReplyTo() != null ? comment.getInReplyTo().getId() : null, comment.getType(), comment.getContent(), comment.getCreatedDate(),
                comment.getLastModifiedDate());
    }
}
