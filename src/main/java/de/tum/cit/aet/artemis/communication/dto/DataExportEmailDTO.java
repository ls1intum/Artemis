package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.core.domain.DataExport;

/**
 * DTO carrying the data export fields referenced from mail templates rendered by the
 * {@link de.tum.cit.aet.artemis.communication.service.notifications.MailService}.
 */
public record DataExportEmailDTO(Long id, String userLogin) {

    public static DataExportEmailDTO from(DataExport dataExport) {
        return new DataExportEmailDTO(dataExport.getId(), dataExport.getUser().getLogin());
    }
}
