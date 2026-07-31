package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;

/**
 * DTO for returning a persisted global notification setting.
 *
 * @param id               the unique identifier of the setting
 * @param userId           the identifier of the user owning the setting
 * @param notificationType the global notification type controlled by the setting
 * @param enabled          whether notifications of this type are enabled for the user
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GlobalNotificationSettingDTO(Long id, Long userId, GlobalNotificationType notificationType, boolean enabled) {

    /**
     * Creates a response DTO from a global notification setting entity.
     *
     * @param setting the persisted global notification setting to convert
     * @return the response DTO containing the REST-safe setting fields
     */
    public static GlobalNotificationSettingDTO from(GlobalNotificationSetting setting) {
        return new GlobalNotificationSettingDTO(setting.getId(), setting.getUserId(), setting.getNotificationType(), setting.getEnabled());
    }
}
