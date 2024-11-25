package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto;

/**
 * Minimal information a recipient of an email needs to provide.
 */
public interface IMailRecipientUserDTO {

    String langKey = "";

    String email = "";

    String langKey();

    String email();
}
