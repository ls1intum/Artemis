package de.tum.cit.aet.artemis.exercise.dto.review;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommentDTO(Long id, Long threadId, Long authorId, String authorName, CommentType type, CommentContentDTO content, Instant createdDate, Instant lastModifiedDate) {

    /**
     * Maps a Comment entity to a DTO.
     *
     * @param comment the comment entity
     */
    public CommentDTO(Comment comment) {
        this(comment.getId(), comment.getThread() != null ? comment.getThread().getId() : null, comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                extractAuthorName(comment.getAuthor()), comment.getType(), comment.getContent(), comment.getCreatedDate(), comment.getLastModifiedDate());
    }

    private static String extractAuthorName(User author) {
        if (author == null) {
            return null;
        }
        String name = author.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return null;
    }
}
