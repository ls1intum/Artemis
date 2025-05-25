package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for updating the enabled state of an email notification setting.
 *
 * @param enabled whether the email notification type is enabled
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateEmailNotificationSettingDTO(Boolean enabled) {
}
