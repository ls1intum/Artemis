package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.password_reset_mail;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

public record PasswordResetRecipientDTO(String langKey, String email, String login, String resetKey) implements IMailRecipientUserDTO {

    public static PasswordResetRecipientDTO of(User user) {
        return new PasswordResetRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getResetKey());
    }
}
