package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.communication.domain.notification.SystemNotification;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SystemNotificationDTO(@NotNull Long id, String title, String text, ZonedDateTime notificationDate, ZonedDateTime expireDate, SystemNotificationType type) {

    public static SystemNotificationDTO of(SystemNotification systemNotification) {
        return new SystemNotificationDTO(systemNotification.getId(), systemNotification.getTitle(), systemNotification.getText(), systemNotification.getNotificationDate(),
                systemNotification.getExpireDate(), systemNotification.getType());
    }
}
