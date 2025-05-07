package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;

/**
 * DTO for creating an Answer Post with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAnswerPostDTO(String content, Post post) {

    public AnswerPost toEntity() {
        AnswerPost answer = new AnswerPost();
        answer.setContent(this.content);
        answer.setPost(this.post);
        return answer;
    }

}
