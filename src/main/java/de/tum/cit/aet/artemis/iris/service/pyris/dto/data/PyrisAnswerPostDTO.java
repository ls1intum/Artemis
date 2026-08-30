package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;

/**
 * Pyris DTO for the answers of a post.
 *
 * @param id           answer post id
 * @param content      content of the answer post, or {@code null} when redacted
 * @param resolvesPost resolves the post
 * @param userID       author user id
 * @param redacted     {@code true} when the author opted out of AI and the content was suppressed
 */
@JsonInclude
public record PyrisAnswerPostDTO(Long id, String content, boolean resolvesPost, Long userID, boolean redacted) {

    public PyrisAnswerPostDTO(AnswerPost answerPost) {
        this(answerPost.getId(), answerPost.getContent(), answerPost.doesResolvePost(), answerPost.getAuthor() != null ? answerPost.getAuthor().getId() : null, false);
    }

    /**
     * Creates a redacted DTO for an answer post whose author has opted out of AI.
     * The content is suppressed so that Iris is aware the message exists without seeing its text.
     *
     * @param answerPost the answer post to redact
     * @return a {@link PyrisAnswerPostDTO} with {@code content = null} and {@code redacted = true}
     */
    public static PyrisAnswerPostDTO redacted(AnswerPost answerPost) {
        return new PyrisAnswerPostDTO(answerPost.getId(), null, answerPost.doesResolvePost(), answerPost.getAuthor() != null ? answerPost.getAuthor().getId() : null, true);
    }
}
