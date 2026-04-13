package de.tum.cit.aet.artemis.exercise.dto.review;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "DTO representing a review comment.")
public record CommentDTO(@Schema(description = "Comment identifier.") @NotNull Long id, @Schema(description = "Identifier of the owning comment thread.") @NotNull Long threadId,
        @Schema(description = "Display name of the author, if available.") String authorName, @Schema(description = "Type of the comment.") @NotNull CommentType type,
        @Schema(description = "Polymorphic content payload of the comment.") @NotNull CommentContentDTO content,
        @Schema(description = "Identifier of the initial exercise version, if available.") Long initialVersionId,
        @Schema(description = "Initial commit SHA for repository-based comments, if available.") String initialCommitSha,
        @Schema(description = "Creation timestamp.") @NotNull Instant createdDate, @Schema(description = "Last modification timestamp.") @NotNull Instant lastModifiedDate) {

    /**
     * Maps a Comment entity to a DTO.
     *
     * @param comment the comment entity
     */
    public CommentDTO(Comment comment) {
        this(comment.getId(), comment.getThread().getId(), extractAuthorName(comment.getAuthor()), comment.getType(), comment.getContent(),
                comment.getInitialVersion() != null ? comment.getInitialVersion().getId() : null, comment.getInitialCommitSha(), comment.getCreatedDate(),
                comment.getLastModifiedDate());
    }

    /**
     * Maps all comments of a thread to DTOs in deterministic order.
     *
     * @param thread the source thread
     * @return mapped comments ordered by creation date and id
     */
    public static List<CommentDTO> fromThread(CommentThread thread) {
        if (thread.getComments() == null || thread.getComments().isEmpty()) {
            return List.of();
        }
        return thread.getComments().stream().sorted(Comparator.comparing(Comment::getCreatedDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comment::getId,
                Comparator.nullsLast(Comparator.naturalOrder()))).map(CommentDTO::new).collect(Collectors.toUnmodifiableList());
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
