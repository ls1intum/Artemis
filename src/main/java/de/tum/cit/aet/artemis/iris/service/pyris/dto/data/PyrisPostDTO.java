package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;

/**
 * Pyris DTO for a post.
 *
 * @param id      post id
 * @param content content of the post
 * @param answers answers to the post
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisPostDTO(Long id, String content, Set<PyrisAnswerPostDTO> answers) {

    public PyrisPostDTO(Post post) {
        this(post.getId(), post.getContent(), post.getAnswers().stream().map(PyrisAnswerPostDTO::new).collect(Collectors.toSet()));
    }
}
