package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.saml2_set_password_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the recipient of the SAML2SetPasswordMail
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SAML2SetPasswordMailRecipientDTO(String langKey, String email, String login, String resetKey) implements IMailRecipientUserDTO {

    public static SAML2SetPasswordMailRecipientDTO of(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getResetKey() == null) {
            throw new IllegalStateException("Reset key is not set");
        }
        return new SAML2SetPasswordMailRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getResetKey());
    }
}
