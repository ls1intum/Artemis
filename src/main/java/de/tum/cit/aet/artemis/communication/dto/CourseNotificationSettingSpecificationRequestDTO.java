package de.tum.cit.aet.artemis.communication.dto;

import java.util.Map;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

public record CourseNotificationSettingSpecificationRequestDTO(Map<Short, Map<NotificationChannelOption, Boolean>> notificationTypeChannels) {
}
