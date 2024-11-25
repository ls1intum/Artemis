package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_failed_mail;

import de.tum.cit.aet.artemis.core.domain.DataExport;

public record DataExportFailedContentDTO(String reason, String exportUsername) {

    public static DataExportFailedContentDTO of(Exception exception, DataExport dataExport) {
        return new DataExportFailedContentDTO(exception.getMessage(), dataExport.getUser().getLogin());
    }
}
