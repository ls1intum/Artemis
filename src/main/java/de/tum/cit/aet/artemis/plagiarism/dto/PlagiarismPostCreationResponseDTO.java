package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;

/**
 * DTO for posts created in the plagiarism context.
 * Exposes only the fields relevant for plagiarism workflows.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismPostCreationResponseDTO(Long id, String content, String title, Boolean visibleForStudents, ZonedDateTime creationDate, UserRole authorRole,
        boolean resolved, DisplayPriority displayPriority, PlagiarismCaseDTO plagiarismCase) {

    /**
     * Creates a {@link PlagiarismPostCreationResponseDTO} from a {@link Post} entity.
     *
     * @param post the persisted post-entity
     * @return a DTO containing the relevant plagiarism-related post-data
     */
    public static PlagiarismPostCreationResponseDTO of(@NonNull Post post) {
        return new PlagiarismPostCreationResponseDTO(post.getId(), post.getContent(), post.getTitle(), post.isVisibleForStudents(), post.getCreationDate(), post.getAuthorRole(),
                post.isResolved(), post.getDisplayPriority(), PlagiarismCaseDTO.of(post.getPlagiarismCase()));
    }
}
