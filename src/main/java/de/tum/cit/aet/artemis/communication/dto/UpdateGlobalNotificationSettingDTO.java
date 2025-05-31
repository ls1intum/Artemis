package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for updating the enabled state of an global notification setting.
 *
 * @param enabled whether the global notification type is enabled
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateGlobalNotificationSettingDTO(boolean enabled) {
}
