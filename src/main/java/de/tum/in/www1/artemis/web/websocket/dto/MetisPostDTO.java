package de.tum.in.www1.artemis.web.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.Post;

/**
 * DTO that is included as payload for post related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MetisPostDTO {

    private Post post;

    private MetisPostAction action;

    public MetisPostDTO(Post post, MetisPostAction action) {
        this.post = post;
        this.action = action;
    }

    public Post getPost() {
        return post;
    }

    public MetisPostAction getAction() {
        return action;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public void setAction(MetisPostAction action) {
        this.action = action;
    }
}
