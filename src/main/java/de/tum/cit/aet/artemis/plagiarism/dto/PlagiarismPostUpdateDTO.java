package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * DTO for updating a PlagiarismPost with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismPostUpdateDTO(Long id, String title, String content) {

    /**
     * Creates a {@link PlagiarismPostUpdateDTO} from a {@link Post} entity.
     *
     * @param post the post-entity to be updated
     * @return an update DTO containing the mutable fields of the post
     * @throws BadRequestAlertException if the post has no id
     */
    public static PlagiarismPostUpdateDTO of(@NonNull Post post) {
        if (post.getId() == null) {
            throw new BadRequestAlertException("The plagiarism post must have an id.", "PlagiarismPost", "idNull");
        }

        return new PlagiarismPostUpdateDTO(post.getId(), post.getTitle(), post.getContent());
    }
}
