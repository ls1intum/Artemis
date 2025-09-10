package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;

/**
 * Pyris DTO for the answers of a post.
 *
 * @param id           answer post id
 * @param content      content of the answer post
 * @param resolvesPost resolves the post
 */
@JsonInclude
public record PyrisAnswerPostDTO(Long id, String content, boolean resolvesPost, Long userID) {

    public PyrisAnswerPostDTO(AnswerPost answerPost) {
        this(answerPost.getId(), answerPost.getContent(), answerPost.doesResolvePost(), answerPost.getAuthor() != null ? answerPost.getAuthor().getId() : null);
    }
}
