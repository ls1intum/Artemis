package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_successful_mail;

import de.tum.cit.aet.artemis.core.domain.DataExport;

public record DataExportSuccessfulContentDTO(String userLogin) {

    public static DataExportSuccessfulContentDTO of(DataExport dataExport) {
        return new DataExportSuccessfulContentDTO(dataExport.getUser().getLogin());
    }
}
