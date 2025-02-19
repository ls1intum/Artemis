package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.notifications;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the recipient of a notification mail.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NotificationMailRecipientDTO(String langKey, String email, String name, String login) implements IMailRecipientUserDTO {

    public static NotificationMailRecipientDTO of(User user) {
        return new NotificationMailRecipientDTO(user.getLangKey(), user.getEmail(), user.getName(), user.getLogin());
    }
}
