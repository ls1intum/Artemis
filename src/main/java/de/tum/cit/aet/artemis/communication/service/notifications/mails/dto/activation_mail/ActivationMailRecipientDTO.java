package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.activation_mail;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

public record ActivationMailRecipientDTO(String langKey, String email, String login, String activationKey) implements IMailRecipientUserDTO {

    public static ActivationMailRecipientDTO of(User user) {
        return new ActivationMailRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin(), user.getActivationKey());
    }
}
