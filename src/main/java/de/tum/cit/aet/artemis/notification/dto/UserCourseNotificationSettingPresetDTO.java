package de.tum.cit.aet.artemis.notification.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserCourseNotificationSettingPresetDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String identifier,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Short typeId, Map<String, Map<NotificationChannelOption, Boolean>> presetMap) {
}
