package de.tum.cit.aet.artemis.notification.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.notification.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.notification.domain.notification.SystemNotification;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for creating and updating SystemNotifications.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 * Missing or null dates still reach the existing service validation; the schema describes successful writes.
 *
 * @param id               absent on creation, identifies an existing notification on update
 * @param title            optional replacement title
 * @param text             optional replacement text
 * @param notificationDate start instant, required and before expiry for a successful write
 * @param expireDate       expiry instant, required for a successful write
 * @param type             optional replacement notification type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SystemNotificationUpdateDTO(@Schema(nullable = true, description = "Omit on creation; required on update") @Nullable Long id,
        @Schema(nullable = true) @Nullable String title, @Schema(nullable = true) @Nullable String text,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Start instant; must precede expireDate") @Nullable ZonedDateTime notificationDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Expiry instant; both dates are required for a successful write") @Nullable ZonedDateTime expireDate,
        @Schema(nullable = true) @Nullable SystemNotificationType type) {

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
