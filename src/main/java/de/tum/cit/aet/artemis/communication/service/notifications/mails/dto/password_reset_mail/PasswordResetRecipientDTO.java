package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.password_reset_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the password reset mail recipient
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PasswordResetRecipientDTO(String langKey, String email, String login, String resetKey) implements IMailRecipientUserDTO {

    public static PasswordResetRecipientDTO of(User user) {
        return new PasswordResetRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getResetKey());
    }
}
