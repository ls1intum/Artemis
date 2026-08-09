package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisAuthorRole;

/**
 * Pyris DTO for the answers of a post.
 *
 * @param id           answer post id
 * @param content      content of the answer post, or {@code null} when redacted
 * @param resolvesPost resolves the post
 * @param userID       author user id
 * @param redacted     {@code true} when the author opted out of AI and the content was suppressed
 * @param authorRole   the author's role in the course, see {@link PyrisAuthorRole}; {@code null} when it could not be resolved
 */
@JsonInclude
public record PyrisAnswerPostDTO(Long id, String content, boolean resolvesPost, Long userID, boolean redacted, @Nullable String authorRole) {

    public PyrisAnswerPostDTO(AnswerPost answerPost, @Nullable UserRole role) {
        this(answerPost.getId(), answerPost.getContent(), answerPost.doesResolvePost(), answerPost.getAuthor() != null ? answerPost.getAuthor().getId() : null, false,
                PyrisAuthorRole.of(answerPost.getAuthor(), role));
    }

    /**
     * Creates a redacted DTO for an answer post whose author has opted out of AI.
     * The content is suppressed so that Iris is aware the message exists without seeing its text.
     * The role is still sent: it carries no content of the author and lets Iris place the message in the thread.
     *
     * @param answerPost the answer post to redact
     * @param role       the author's role in the course, or {@code null} if it could not be resolved
     * @return a {@link PyrisAnswerPostDTO} with {@code content = null} and {@code redacted = true}
     */
    public static PyrisAnswerPostDTO redacted(AnswerPost answerPost, @Nullable UserRole role) {
        return new PyrisAnswerPostDTO(answerPost.getId(), null, answerPost.doesResolvePost(), answerPost.getAuthor() != null ? answerPost.getAuthor().getId() : null, true,
                PyrisAuthorRole.of(answerPost.getAuthor(), role));
    }
}
