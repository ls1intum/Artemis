package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_successful_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.IMailRecipientUserDTO;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO for the admin recipient of the data export successful mail notification.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DataExportSuccessfulMailAdminRecipientDTO(String langKey, String email) implements IMailRecipientUserDTO {

    public static DataExportSuccessfulMailAdminRecipientDTO of(User user) {
        return new DataExportSuccessfulMailAdminRecipientDTO(user.getLangKey(), user.getEmail());
    }
}
