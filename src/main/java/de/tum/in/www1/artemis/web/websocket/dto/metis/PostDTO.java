package de.tum.in.www1.artemis.web.websocket.dto.metis;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.Post;

/**
 * DTO that is included as payload for post related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PostDTO {

    private Post post;

    private MetisCrudAction action;

    public PostDTO(Post post, MetisCrudAction action) {
        this.post = post;
        this.action = action;
    }

    public Post getPost() {
        return post;
    }

    public MetisCrudAction getAction() {
        return action;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public void setAction(MetisCrudAction action) {
        this.action = action;
    }
}
