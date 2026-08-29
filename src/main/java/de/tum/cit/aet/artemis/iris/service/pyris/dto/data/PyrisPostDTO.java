package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
     * its author's course role.
     *
     * @param post          the thread root, with its answers loaded
     * @param rolesByUserId course roles of the human authors, as resolved by a single query; authors
     *                          missing from the map are treated as students
     * @return the thread as Pyris expects it
     */
    public static PyrisPostDTO of(Post post, Map<Long, UserRole> rolesByUserId) {
        var answers = post.getAnswers().stream().sorted(CHRONOLOGICAL).map(answer -> {
            String role = authorRoleOf(answer.getAuthor(), rolesByUserId);
            return AiSelectionDecision.NO_AI.equals(answer.getAuthor() != null ? answer.getAuthor().getSelectedLLMUsage() : null) ? PyrisAnswerPostDTO.redacted(answer, role)
                    : new PyrisAnswerPostDTO(answer, role);
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
}
