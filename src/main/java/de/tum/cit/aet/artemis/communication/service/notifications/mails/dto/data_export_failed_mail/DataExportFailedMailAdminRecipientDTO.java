package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_failed_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the recipient of the data export failed mail for the admin
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DataExportFailedMailAdminRecipientDTO(String langKey, String email, String login) implements IMailRecipientUserDTO {

    public static DataExportFailedMailAdminRecipientDTO of(User user) {
        return new DataExportFailedMailAdminRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin());
    }
}
