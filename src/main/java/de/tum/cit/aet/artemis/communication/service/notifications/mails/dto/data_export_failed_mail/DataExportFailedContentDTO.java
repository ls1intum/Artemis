package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_failed_mail;

import de.tum.cit.aet.artemis.core.domain.DataExport;

/**
 * DTO for the content of a data export failed mail
 *
 * @param reason         The reason why the data export failed
 * @param exportUsername The username of the user who initiated the data export
 */
public record DataExportFailedContentDTO(String reason, String exportUsername) {

    public static DataExportFailedContentDTO of(Exception exception, DataExport dataExport) {
        return new DataExportFailedContentDTO(exception.getMessage(), dataExport.getUser().getLogin());
    }
}
