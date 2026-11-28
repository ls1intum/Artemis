package de.tum.cit.aet.artemis.notification.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Partial notification channel overrides. An empty map selects custom settings without changing individual flags.
 * Missing channel keys and null values leave existing specifications unchanged; explicit false disables the channel.
 *
 * @param notificationTypeChannels notification type ids as JSON object keys, each with the channel overrides to apply
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationSettingSpecificationRequestDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels) {
}
