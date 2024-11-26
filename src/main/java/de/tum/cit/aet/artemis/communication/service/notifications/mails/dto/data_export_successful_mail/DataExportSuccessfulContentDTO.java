package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_successful_mail;

import de.tum.cit.aet.artemis.core.domain.DataExport;

/**
 * DTO for the content of a data export successful mail.
 *
 * @param userLogin the login of the user who requested the data export
 */
public record DataExportSuccessfulContentDTO(String userLogin) {

    public static DataExportSuccessfulContentDTO of(DataExport dataExport) {
        return new DataExportSuccessfulContentDTO(dataExport.getUser().getLogin());
    }
}
