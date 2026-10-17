package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisAuthorRole;

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
     * Creates the thread Pyris receives.
     * <p>
     * Both per-author maps are looked up once for the whole thread rather than once per message: reading either
     * from the answer authors here would issue a query per answer post on an Iris request path.
     *
     * @param post      the post to convert, with its answers loaded
     * @param roles     the course roles of the thread participants, keyed by user id; missing entries yield a {@code null} role
     * @param decisions the LLM usage decisions of the thread's authors, keyed by user id, as loaded in one query by
     *                      {@code UserAiPreferenceService.findDecisions}
     */
    public PyrisPostDTO(Post post, Map<Long, UserRole> roles, Map<Long, AiSelectionDecision> decisions) {
        this(post.getId(), post.getContent(), toOrderedAnswerDTOs(post, roles, decisions), post.getAuthor().getId(),
                PyrisAuthorRole.of(post.getAuthor(), roles.get(post.getAuthor().getId())));
    }

    private static List<PyrisAnswerPostDTO> toOrderedAnswerDTOs(Post post, Map<Long, UserRole> roles, Map<Long, AiSelectionDecision> decisions) {
        return post.getAnswers().stream()
                .sorted(Comparator.comparing(AnswerPost::getCreationDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(AnswerPost::getId)).map(answer -> {
                    UserRole role = answer.getAuthor() != null ? roles.get(answer.getAuthor().getId()) : null;
                    boolean optedOut = AiSelectionDecision.NO_AI.equals(decisionOf(decisions, answer));
                    return optedOut ? PyrisAnswerPostDTO.redacted(answer, role) : new PyrisAnswerPostDTO(answer, role);
                }).toList();
    }

    /**
     * The ids of the authors of a post's answers, which is exactly the set of decisions this DTO needs. Callers load them
     * in one query through {@code UserAiPreferenceService.findDecisions} instead of one per answer.
     *
     * @param post the post whose answers are forwarded
     * @return the distinct author ids, skipping answers without a persisted author
     */
    public static Set<Long> answerAuthorIds(Post post) {
        return post.getAnswers().stream().map(answer -> answer.getAuthor() == null ? null : answer.getAuthor().getId()).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static AiSelectionDecision decisionOf(Map<Long, AiSelectionDecision> decisions, AnswerPost answer) {
        if (answer.getAuthor() == null || answer.getAuthor().getId() == null) {
            return null;
        }
        return decisions.get(answer.getAuthor().getId());
    }
}
