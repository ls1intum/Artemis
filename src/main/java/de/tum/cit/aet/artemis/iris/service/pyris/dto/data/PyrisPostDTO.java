package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * Pyris DTO for a post.
 * <p>
 * {@code answers} is an ordered {@link List}, oldest first. The order carries meaning: Iris addresses the most
 * recent message of a thread, so it must be able to tell which reply that is. Sorting happens here rather than in
 * Pyris so no creation timestamps have to be sent.
 *
 * @param id         post id
 * @param content    content of the post
 * @param answers    answers to the post, ordered oldest first
 * @param userID     author user id
 * @param authorRole the author's role in the course, see {@link PyrisAuthorRole}; {@code null} when it could not be resolved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPostDTO(Long id, String content, List<PyrisAnswerPostDTO> answers, Long userID, @Nullable String authorRole) {

    /**
     * Creates a DTO without resolved author roles. Use {@link #PyrisPostDTO(Post, Map)} where Iris needs to tell
     * the participants of a thread apart.
     *
     * @param post the post to convert, with its answers loaded
     */
    public PyrisPostDTO(Post post) {
        this(post, Map.of());
    }

    /**
     * Creates a DTO with author roles resolved from the given map.
     *
     * @param post  the post to convert, with its answers loaded
     * @param roles the course roles of the thread participants, keyed by user id; missing entries yield a {@code null} role
     */
    public PyrisPostDTO(Post post, Map<Long, UserRole> roles) {
        this(post.getId(), post.getContent(), toOrderedAnswerDTOs(post, roles), post.getAuthor().getId(),
                PyrisAuthorRole.of(post.getAuthor(), roles.get(post.getAuthor().getId())));
    }

    private static List<PyrisAnswerPostDTO> toOrderedAnswerDTOs(Post post, Map<Long, UserRole> roles) {
        return post.getAnswers().stream()
                .sorted(Comparator.comparing(AnswerPost::getCreationDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(AnswerPost::getId)).map(answer -> {
                    UserRole role = answer.getAuthor() != null ? roles.get(answer.getAuthor().getId()) : null;
                    boolean optedOut = AiSelectionDecision.NO_AI.equals(answer.getAuthor() != null ? answer.getAuthor().getSelectedLLMUsage() : null);
                    return optedOut ? PyrisAnswerPostDTO.redacted(answer, role) : new PyrisAnswerPostDTO(answer, role);
                }).collect(Collectors.toList());
    }
}
