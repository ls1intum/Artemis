package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.dto.AuthorDTO;

/**
 * DTO for posts created in the plagiarism context.
 * Exposes only the fields relevant for plagiarism workflows.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismPostCreationResponseDTO(@NotNull(message = "The post must have an id.") Long id, @NotNull(message = "The post must have contents.") String content,
        @NotNull(message = "The post must have a title.") String title, Boolean visibleForStudents, @NotNull ZonedDateTime creationDate, @NotNull AuthorDTO authorDTO,
        @NotNull UserRole authorRole, boolean resolved, DisplayPriority displayPriority,
        @NotNull(message = "The post must be associated with a plagiarism case.") PlagiarismCaseDTO plagiarismCase) {

    /**
     * Creates a {@link PlagiarismPostCreationResponseDTO} from a {@link Post} entity.
     *
     * @param post the persisted post-entity
     * @return a DTO containing the relevant plagiarism-related post-data
     */
    public static PlagiarismPostCreationResponseDTO of(@NotNull Post post) {
        return new PlagiarismPostCreationResponseDTO(post.getId(), post.getContent(), post.getTitle(), post.isVisibleForStudents(), post.getCreationDate(),
                AuthorDTO.fromUser(post.getAuthor()), post.getAuthorRole(), post.isResolved(), post.getDisplayPriority(), PlagiarismCaseDTO.of(post.getPlagiarismCase()));
    }
}
