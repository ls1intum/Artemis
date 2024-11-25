package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_successful_mail;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

public record DataExportSuccessfulMailAdminRecipientDTO(String langKey, String email) implements IMailRecipientUserDTO {

    public static DataExportSuccessfulMailAdminRecipientDTO of(User user) {
        return new DataExportSuccessfulMailAdminRecipientDTO(user.getLangKey(), user.getEmail());
    }
}
