package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.communication.domain.notification.SystemNotification;

/**
 * DTO containing the relevant information of a {@link SystemNotification}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SystemNotificationDTO(@NotNull Long id, String title, String text, ZonedDateTime notificationDate, ZonedDateTime expireDate, SystemNotificationType type) {

    /**
     * Creates a DTO representation of a {@link SystemNotification}.
     *
     * @param systemNotification the system notification entity
     * @return the corresponding system notification DTO
     */
    public static SystemNotificationDTO of(SystemNotification systemNotification) {
        Objects.requireNonNull(systemNotification, "SystemNotification must not be null");
        return new SystemNotificationDTO(systemNotification.getId(), systemNotification.getTitle(), systemNotification.getText(), systemNotification.getNotificationDate(),
                systemNotification.getExpireDate(), systemNotification.getType());
    }
}
