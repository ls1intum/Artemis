package de.tum.in.www1.artemis.web.websocket.dto.metis;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.notification.Notification;

/**
 * DTO that is included as payload for post related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostDTO(Post post, MetisCrudAction action, Conversation conversation, Notification notification) {

    public PostDTO(Post post, MetisCrudAction action) {
        this(post, action, null, null);
    }
}
