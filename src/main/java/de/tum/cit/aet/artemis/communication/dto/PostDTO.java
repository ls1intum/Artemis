package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.notification.Notification;

/**
 * DTO that is included as payload for post related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostDTO(Post post, MetisCrudAction action, Notification notification) {

    public PostDTO(Post post, MetisCrudAction action) {
        this(post, action, null);
    }
}
