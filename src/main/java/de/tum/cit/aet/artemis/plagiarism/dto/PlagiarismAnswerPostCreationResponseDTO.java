package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.dto.AuthorDTO;

/**
 * DTO for returning a created plagiarism AnswerPost.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismAnswerPostCreationResponseDTO(Long id, String content, Long postId, AuthorDTO authorDTO, UserRole userRole, Boolean resolvesPost,
        Boolean hasForwardedMessages, ZonedDateTime creationDate) {

    /**
     * Creates a {@link PlagiarismAnswerPostCreationResponseDTO} from an {@link AnswerPost} entity.
     *
     * @param answerPost the persisted answer post
     * @return response DTO for the created answer post
     */
    public static PlagiarismAnswerPostCreationResponseDTO of(@NonNull AnswerPost answerPost) {
        return new PlagiarismAnswerPostCreationResponseDTO(answerPost.getId(), answerPost.getContent(), answerPost.getPost() != null ? answerPost.getPost().getId() : null,
                AuthorDTO.fromUser(answerPost.getAuthor()), answerPost.getAuthorRole(), answerPost.doesResolvePost(), answerPost.getHasForwardedMessages(),
                answerPost.getCreationDate());
    }
}
