package de.tum.cit.aet.artemis.plagiarism.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * DTO for creating a Plagiarism AnswerPost with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismAnswerPostCreationDTO(Long id, @NotNull(message = "The post must have contents.") String content,
        @NotNull(message = "The answer post must be associated with a post.") Long postId, @NotNull Boolean resolvesPost, @NotNull Boolean hasForwardedMessages) {

    private static final String PLAGIARISM_ANSWER_POST_ENTITY_NAME = "PlagiarismAnswerPost";

    /**
     * Converts this DTO to an AnswerPost entity.
     * <p>
     * Note: author, authorRole, resolvesPost, creationDate, etc. are set server-side.
     *
     * @return a new AnswerPost entity with the data from this DTO
     */
    public AnswerPost toEntity() {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setId(id);
        answerPost.setContent(content);
        // only set the reference; actual post is loaded from DB in the service
        Post post = new Post();
        post.setId(postId);
        answerPost.setPost(post);
        answerPost.setResolvesPost(resolvesPost);
        answerPost.setHasForwardedMessages(hasForwardedMessages);
        return answerPost;
    }

    /**
     * Creates a {@link PlagiarismAnswerPostCreationDTO} from an {@link AnswerPost} entity.
     *
     * @param answerPost the answer-post entity
     * @return a DTO containing the data required to create an answer post
     */
    public static PlagiarismAnswerPostCreationDTO of(@NotNull AnswerPost answerPost) {
        if (answerPost.getPost() == null || answerPost.getPost().getId() == null) {
            throw new BadRequestAlertException("The answer post must be associated with a post.", PLAGIARISM_ANSWER_POST_ENTITY_NAME, "postIdMissing");
        }

        return new PlagiarismAnswerPostCreationDTO(answerPost.getId(), answerPost.getContent(), answerPost.getPost().getId(), answerPost.doesResolvePost(),
                answerPost.getHasForwardedMessages());
    }
}
