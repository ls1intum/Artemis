package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;

/**
 * Pyris DTO for a post.
 *
 * @param id      post id
 * @param content content of the post
 * @param answers answers to the post
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPostDTO(Long id, String content, Set<PyrisAnswerPostDTO> answers, Long userID) {

    /**
     * @param post      the post together with its answers
     * @param decisions the LLM usage decisions of the answer authors, loaded in one query by
     *                      {@code UserAiPreferenceService.findDecisions}. Reading each author's decision here instead
     *                      would issue a query per answer post, which is why it is passed in.
     */
    public PyrisPostDTO(Post post, Map<Long, AiSelectionDecision> decisions) {
        this(post.getId(), post.getContent(),
                post.getAnswers().stream()
                        .map(answer -> AiSelectionDecision.NO_AI.equals(decisionOf(decisions, answer)) ? PyrisAnswerPostDTO.redacted(answer) : new PyrisAnswerPostDTO(answer))
                        .collect(Collectors.toSet()),
                post.getAuthor().getId());
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
