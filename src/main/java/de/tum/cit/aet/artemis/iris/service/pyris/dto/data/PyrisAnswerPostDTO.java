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
 * @param authorRole   {@code IRIS}, {@code INSTRUCTOR}, {@code TUTOR} or {@code STUDENT}; lets Iris tell its own
 *                         earlier reply apart from a student's follow-up and from staff answers
 */
@JsonInclude
public record PyrisAnswerPostDTO(Long id, String content, boolean resolvesPost, Long userID, boolean redacted, String authorRole) {

    public PyrisAnswerPostDTO(AnswerPost answerPost, String authorRole) {
        this(answerPost.getId(), answerPost.getContent(), answerPost.doesResolvePost(), answerPost.getAuthor() != null ? answerPost.getAuthor().getId() : null, false, authorRole);
    }

    /**
     * Creates a redacted DTO for an answer post whose author has opted out of AI.
     * The content is suppressed so that Iris is aware the message exists without seeing its text.
     *
     * @param answerPost the answer post to redact
     * @param authorRole the author's course role, which is not sensitive and is still sent
     * @return a {@link PyrisAnswerPostDTO} with {@code content = null} and {@code redacted = true}
     */
    public static PyrisAnswerPostDTO redacted(AnswerPost answerPost, String authorRole) {
        return new PyrisAnswerPostDTO(answerPost.getId(), null, answerPost.doesResolvePost(), answerPost.getAuthor() != null ? answerPost.getAuthor().getId() : null, true,
                authorRole);
    }
}
