package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto;

/**
 * Minimal information a recipient of an email needs to provide to be able to receive an email.
 */
public interface IMailRecipientUserDTO {

    String langKey();

    String email();
}
