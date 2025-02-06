package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.activation_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the recipient of the activation mail
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActivationMailRecipientDTO(String langKey, String email, String login, String activationKey) implements IMailRecipientUserDTO {

    public static ActivationMailRecipientDTO of(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getActivationKey() == null) {
            throw new IllegalStateException("Activation key is required for account activation");
        }
        return new ActivationMailRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getActivationKey());
    }
}
