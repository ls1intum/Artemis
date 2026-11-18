package de.tum.cit.aet.artemis.notification.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationSettingSpecificationRequestDTO(Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels) {
}
