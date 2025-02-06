package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_failed_mail;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DataExport;

/**
 * DTO for the content of a data export failed mail
 *
 * @param reason         The reason why the data export failed
 * @param exportUsername The username of the user who initiated the data export
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DataExportFailedContentDTO(String reason, String exportUsername) {

    public static DataExportFailedContentDTO of(Exception exception, DataExport dataExport) {
        if (exception == null || dataExport == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        if (dataExport.getUser() == null) {
            throw new IllegalStateException("DataExport user cannot be null");
        }
        return new DataExportFailedContentDTO(exception.getMessage(), dataExport.getUser().getLogin());
    }
}
