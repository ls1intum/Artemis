package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal information a recipient of an email needs to provide to be able to receive an email.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface IMailRecipientUserDTO {

    String langKey();

    String email();
}
