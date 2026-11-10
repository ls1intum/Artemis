package de.tum.cit.aet.artemis.notification.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Effective per-course notification preferences for the current user.
 *
 * @param selectedPreset           the selected preset id, including 0 for custom/effective test-account settings
 * @param notificationTypeChannels notification type ids as JSON object keys, each with all three effective channel flags; omitted when empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationSettingInfoDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Short selectedPreset,
        Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels) {
}
