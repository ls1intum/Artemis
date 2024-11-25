package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_failed_mail;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

public record DataExportFailedMailAdminRecipientDTO(String langKey, String email, String login) implements IMailRecipientUserDTO {

    public static DataExportFailedMailAdminRecipientDTO of(User user) {
        return new DataExportFailedMailAdminRecipientDTO(user.getLangKey(), user.getEmail(), user.getLogin());
    }
}
