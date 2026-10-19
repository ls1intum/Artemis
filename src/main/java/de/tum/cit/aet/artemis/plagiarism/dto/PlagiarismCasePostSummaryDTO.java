package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCasePostSummaryDTO(Long id, ZonedDateTime creationDate) {

    /**
     * Maps a communication post entity to the plagiarism case post summary DTO.
     *
     * @param post the post entity
     * @return the DTO representation
     */
    public static @Nullable PlagiarismCasePostSummaryDTO fromPost(@Nullable Post post) {
        if (post == null) {
            return null;
        }
        return new PlagiarismCasePostSummaryDTO(post.getId(), post.getCreationDate());
    }
}
