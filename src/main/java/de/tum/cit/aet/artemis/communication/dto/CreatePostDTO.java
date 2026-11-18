package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;

/**
 * DTO for creating a Post with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreatePostDTO(String content, String title, boolean hasForwardedMessages, CreatePostConversationDTO conversation) {

    /**
     * Converts this DTO to a Post entity.
     *
     * @return a new Post entity with the data from this DTO
     */
    public Post toEntity() {
        Post post = new Post();
        post.setContent(this.content);
        post.setTitle(this.title);
        post.setHasForwardedMessages(this.hasForwardedMessages);
        return post;
    }

}
