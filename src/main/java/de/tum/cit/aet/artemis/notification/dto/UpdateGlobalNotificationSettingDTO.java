package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for updating the enabled state of a global notification setting.
 *
 * @param enabled whether the global notification type is enabled; omitted or null values default to false
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateGlobalNotificationSettingDTO(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, defaultValue = "false", description = "Whether this notification is enabled. Omitted or null values default to false.") boolean enabled) {
}
