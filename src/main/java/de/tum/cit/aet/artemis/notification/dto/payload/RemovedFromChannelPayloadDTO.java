package de.tum.cit.aet.artemis.notification.dto.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The payload of the removed from channel notification.
 *
 * @param channelModerator the moderator who performed the change
 * @param channelName      the name of that channel
 * @param channelId        the channel involved
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RemovedFromChannelPayloadDTO(String channelModerator, String channelName, Long channelId) implements CourseNotificationPayloadDTO {
}
