package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.communication.domain.notification.SystemNotification;

/**
 * DTO for creating and updating SystemNotifications.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SystemNotificationUpdateDTO(@Nullable Long id, @Nullable String title, @Nullable String text, @Nullable ZonedDateTime notificationDate,
        @Nullable ZonedDateTime expireDate, @Nullable SystemNotificationType type) {

    /**
     * Creates a SystemNotificationUpdateDTO from the given SystemNotification domain object.
     *
     * @param notification the SystemNotification to convert
     * @return the corresponding DTO
     */
    public static SystemNotificationUpdateDTO of(SystemNotification notification) {
        return new SystemNotificationUpdateDTO(notification.getId(), notification.getTitle(), notification.getText(), notification.getNotificationDate(),
                notification.getExpireDate(), notification.getType());
    }

    /**
     * Creates a new SystemNotification entity from this DTO.
     * Used for create operations.
     *
     * @return a new SystemNotification entity
     */
    public SystemNotification toEntity() {
        SystemNotification notification = new SystemNotification();
        notification.setTitle(title);
        notification.setText(text);
        notification.setNotificationDate(notificationDate);
        notification.setExpireDate(expireDate);
        notification.setType(type);
        return notification;
    }

    /**
     * Applies the DTO values to an existing SystemNotification entity.
     * This updates the managed entity with values from the DTO.
     *
     * @param notification the existing notification to update
     */
    public void applyTo(SystemNotification notification) {
        notification.setTitle(title);
        notification.setText(text);
        notification.setNotificationDate(notificationDate);
        notification.setExpireDate(expireDate);
        notification.setType(type);
    }
}
