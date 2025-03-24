package de.tum.cit.aet.artemis.communication.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseNotificationInfoDTO(Map<Short, String> notificationTypes, NotificationChannelOption[] channels, List<UserCourseNotificationSettingPresetDTO> presets) {
}
