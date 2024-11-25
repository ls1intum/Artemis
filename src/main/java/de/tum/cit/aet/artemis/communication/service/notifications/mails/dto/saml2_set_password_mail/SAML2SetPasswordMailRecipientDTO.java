package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.saml2_set_password_mail;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

public record SAML2SetPasswordMailRecipientDTO(String langKey, String email, String login, String resetKey) implements IMailRecipientUserDTO {

    public static SAML2SetPasswordMailRecipientDTO of(User user) {
        return new SAML2SetPasswordMailRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getResetKey());
    }
}
