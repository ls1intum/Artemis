package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Response DTO representing a plagiarism post after it has been updated with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismPostUpdateResponseDTO(Long id, String title, String content, ZonedDateTime updatedDate, Boolean isSaved, Boolean resolved, Set<Long> answerIds) {

    /**
     * Creates a {@link PlagiarismPostUpdateResponseDTO} from a {@link Post} entity.
     *
     * @param post the updated {@link Post} entity
     * @return a response DTO representing the updated plagiarism post
     */
    public static PlagiarismPostUpdateResponseDTO of(@NonNull Post post) {
        if (post.getId() == null) {
            throw new BadRequestAlertException("The plagiarism post must have an id.", "PlagiarismPost", "idNull");
        }

        return new PlagiarismPostUpdateResponseDTO(post.getId(), post.getTitle(), post.getContent(), post.getUpdatedDate(), post.getIsSaved(), post.isResolved(),
                mapAnswerIds(post));
    }

    /**
     * Extracts the ids of all answers associated with the given post.
     *
     * @param post the post-entity
     * @return a set of answer ids, or an empty set if no answers are present
     */
    private static Set<Long> mapAnswerIds(Post post) {
        if (post.getAnswers() == null || post.getAnswers().isEmpty()) {
            return Set.of();
        }
        return post.getAnswers().stream().map(AnswerPost::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
