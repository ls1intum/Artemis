package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

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

    public PyrisPostDTO(Post post) {
        this(post.getId(), post.getContent(),
                post.getAnswers().stream()
                        .map(answer -> AiSelectionDecision.NO_AI.equals(answer.getAuthor() != null ? answer.getAuthor().getSelectedLLMUsage() : null)
                                ? PyrisAnswerPostDTO.redacted(answer)
                                : new PyrisAnswerPostDTO(answer))
                        .collect(Collectors.toSet()),
                post.getAuthor().getId());
    }
}
