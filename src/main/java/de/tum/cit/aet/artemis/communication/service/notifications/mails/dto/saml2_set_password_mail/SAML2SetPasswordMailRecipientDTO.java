package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.saml2_set_password_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the recipient of the SAML2SetPasswordMail
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SAML2SetPasswordMailRecipientDTO(String langKey, String email, String login, String resetKey) implements IMailRecipientUserDTO {

    /**
     * Factory method to create a {@link SAML2SetPasswordMailRecipientDTO} instance from a {@link User}.
     *
     * @param user The user object used to populate the DTO.
     *                 The {@code resetKey} field in the {@code User} must also not be {@code null}.
     * @return A new {@link SAML2SetPasswordMailRecipientDTO} containing the user's language key, email, login, and reset key.
     * @throws IllegalStateException if the {@code resetKey} field of {@code user} is {@code null}.
     */
    public static SAML2SetPasswordMailRecipientDTO of(User user) {
        if (user.getResetKey() == null) {
            throw new IllegalStateException("Reset key is required");
        }
        return new SAML2SetPasswordMailRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getResetKey());
    }
}
