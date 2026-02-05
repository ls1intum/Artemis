package de.tum.cit.aet.artemis.plagiarism.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * DTO for creating a PlagiarismPost with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismPostCreationDTO(Long id, @NotNull(message = "The post must have contents.") String content, @NotNull(message = "The post must have a title.") String title,
        @NotNull Boolean visibleForStudents, @NotNull Boolean hasForwardedMessages,
        @NotNull(message = "The post must be associated with a plagiarism case.") Long plagiarismCaseId) {

    private static final String PLAGIARISM_POST_ENTITY_NAME = "PlagiarismPost";

    /**
     * Converts this DTO to a PlagiarismPost entity.
     *
     * @return a new Post entity with the data from this DTO
     */
    public Post toEntity() {
        Post post = new Post();
        post.setId(id);
        post.setContent(content);
        post.setTitle(title);
        post.setVisibleForStudents(visibleForStudents);
        post.setHasForwardedMessages(hasForwardedMessages);
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setId(plagiarismCaseId);
        post.setPlagiarismCase(plagiarismCase);
        return post;
    }

    /**
     * Creates a {@link PlagiarismPostCreationDTO} from a {@link Post} entity.
     *
     * @param post the post-entity
     * @return a DTO containing the data required to create a plagiarism post
     */
    public static PlagiarismPostCreationDTO of(@NotNull Post post) {
        if (post.getPlagiarismCase() == null || post.getPlagiarismCase().getId() == null) {
            throw new BadRequestAlertException("The post must be associated with a plagiarism case.", PLAGIARISM_POST_ENTITY_NAME, "plagiarismCaseMissing");
        }
        return new PlagiarismPostCreationDTO(post.getId(), post.getContent(), post.getTitle(), post.isVisibleForStudents(), post.getHasForwardedMessages(),
                post.getPlagiarismCase().getId());
    }
}
