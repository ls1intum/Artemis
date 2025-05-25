package de.tum.cit.aet.artemis.communication.dto;

/**
 * DTO for updating the enabled state of an email notification setting.
 *
 * @param enabled whether the email notification type is enabled
 */
public record UpdateEmailNotificationSettingDTO(Boolean enabled) {
}
