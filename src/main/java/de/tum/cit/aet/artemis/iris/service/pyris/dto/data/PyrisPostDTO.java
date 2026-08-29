package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * Pyris DTO for a post.
 *
 * @param id         post id
 * @param content    content of the post
 * @param answers    answers to the post, oldest first
 * @param userID     author user id
 * @param authorRole {@code IRIS}, {@code INSTRUCTOR}, {@code TUTOR} or {@code STUDENT}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPostDTO(Long id, String content, List<PyrisAnswerPostDTO> answers, Long userID, String authorRole) {

    /**
     * Chronological order, oldest first. Pyris answers the LAST entry, because Artemis re-runs the
     * autonomous tutor on every new message and the newest one is what triggered the run.
     * <p>
     * This was a {@code Set} — {@link Post#getAnswers()} is a {@code HashSet} and the DTOs were
     * collected into another one. {@link PyrisAnswerPostDTO} is a record, so its hash folds in the
     * message content and bucket order is effectively arbitrary. Whenever Iris's own earlier reply
     * happened to land last, Pyris took that as the message to answer, correctly decided its own
     * reply is not a question addressed to it, and silently skipped the student's follow-up.
     */
    private static final Comparator<AnswerPost> CHRONOLOGICAL = Comparator.comparing(AnswerPost::getCreationDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnswerPost::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Builds the thread Pyris receives: the root post plus its answers, oldest first, each tagged with
     * its author's course role, and with opted-out authors' content redacted.
     *
     * @param post          the thread root, with its answers loaded
     * @param rolesByUserId course roles of the human authors, as resolved by a single query; authors
     *                          missing from the map are treated as students
     * @param decisions     the LLM usage decisions of the answer authors, loaded in one query by
     *                          {@code UserAiPreferenceService.findDecisions}. Reading each author's decision here
     *                          instead would issue a query per answer post, which is why it is passed in.
     * @return the thread as Pyris expects it
     */
    public static PyrisPostDTO of(Post post, Map<Long, UserRole> rolesByUserId, Map<Long, AiSelectionDecision> decisions) {
        var answers = post.getAnswers().stream().sorted(CHRONOLOGICAL).map(answer -> {
            String role = authorRoleOf(answer.getAuthor(), rolesByUserId);
            return AiSelectionDecision.NO_AI.equals(decisionOf(decisions, answer)) ? PyrisAnswerPostDTO.redacted(answer, role) : new PyrisAnswerPostDTO(answer, role);
        }).toList();
        return new PyrisPostDTO(post.getId(), post.getContent(), answers, post.getAuthor().getId(), authorRoleOf(post.getAuthor(), rolesByUserId));
    }

    /**
     * The role vocabulary Iris's prompt speaks, which is not {@link UserRole}: the bot's own replies must
     * come through as {@code IRIS} so Iris can recognise them as its own rather than as another course
     * member's answer. Artemis stamps {@link UserRole#USER} on those posts for the client's benefit, so
     * the bot flag — not the stored role — is what decides this.
     */
    private static String authorRoleOf(@Nullable User author, Map<Long, UserRole> rolesByUserId) {
        if (author == null) {
            return null;
        }
        if (author.isBot()) {
            return "IRIS";
        }
        return switch (rolesByUserId.getOrDefault(author.getId(), UserRole.USER)) {
            case INSTRUCTOR -> "INSTRUCTOR";
            case TUTOR -> "TUTOR";
            case USER -> "STUDENT";
        };
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
