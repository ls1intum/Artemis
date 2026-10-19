package de.tum.cit.aet.artemis.notification.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.notification.domain.notification.SystemNotification;

/**
 * DTO for returning system notifications through REST endpoints.
 *
 * @param id               the unique identifier of the system notification
 * @param title            the optional notification title
 * @param text             the optional notification text
 * @param notificationDate the optional date from which the notification should be shown
 * @param expireDate       the optional date after which the notification expires
 * @param type             the optional type of the system notification
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SystemNotificationDTO(Long id, @Nullable String title, @Nullable String text, @Nullable ZonedDateTime notificationDate, @Nullable ZonedDateTime expireDate,
        @Nullable SystemNotificationType type) {

    /**
     * Creates a response DTO from a system notification entity.
     *
     * @param notification the system notification entity to convert
     * @return the response DTO containing the REST-safe notification fields
     */
    public static SystemNotificationDTO from(SystemNotification notification) {
        return new SystemNotificationDTO(notification.getId(), notification.getTitle(), notification.getText(), notification.getNotificationDate(), notification.getExpireDate(),
                notification.getType());
    }
}
