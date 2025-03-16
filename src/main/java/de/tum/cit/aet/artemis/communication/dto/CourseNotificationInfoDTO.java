package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

public record CourseNotificationInfoDTO(Map<Short, String> notificationTypes, NotificationChannelOption[] channels, List<UserCourseNotificationSettingPresetDTO> presets) {
}
