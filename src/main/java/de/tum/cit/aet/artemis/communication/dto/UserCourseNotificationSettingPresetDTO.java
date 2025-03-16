package de.tum.cit.aet.artemis.communication.dto;

import java.util.Map;

import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

public record UserCourseNotificationSettingPresetDTO(String identifier, Short typeId, Map<String, Map<NotificationChannelOption, Boolean>> presetMap) {
}
