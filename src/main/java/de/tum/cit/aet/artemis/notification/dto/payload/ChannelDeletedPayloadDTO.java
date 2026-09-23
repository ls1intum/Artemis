package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the channel deleted notification.
 *
 * @param deletingUser the user who deleted the channel
 * @param channelName  the name of that channel
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChannelDeletedPayloadDTO(String deletingUser, String channelName) implements CourseNotificationPayloadDTO {
}
