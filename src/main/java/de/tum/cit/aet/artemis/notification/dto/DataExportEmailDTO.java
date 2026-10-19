package de.tum.cit.aet.artemis.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.DataExport;

/**
 * DTO carrying the data export fields referenced from mail templates rendered by the
 * {@link de.tum.cit.aet.artemis.notification.service.notifications.MailService}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DataExportEmailDTO(Long id, String userLogin) {

    public static DataExportEmailDTO from(DataExport dataExport) {
        return new DataExportEmailDTO(dataExport.getId(), dataExport.getUser().getLogin());
    }
}
