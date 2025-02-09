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

    /**
     * Factory method to create a {@link DataExportFailedContentDTO} instance based on an exception
     * and the associated {@link DataExport}.
     *
     * @param exception  The exception that caused the data export to fail. Must not be {@code null}.
     * @param dataExport The data export object associated with the failure. Must not be {@code null}.
     *                       The {@code user} field in the {@code DataExport} must also not be {@code null}.
     * @return A new {@link DataExportFailedContentDTO} containing the exception message and export username.
     * @throws IllegalArgumentException if either {@code exception} or {@code dataExport} is {@code null}.
     * @throws IllegalStateException    if the {@code user} field of {@code dataExport} is {@code null}.
     */
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
