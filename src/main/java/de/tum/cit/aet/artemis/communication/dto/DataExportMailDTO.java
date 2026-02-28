package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.core.domain.DataExport;

/**
 * DTO containing only the data export fields needed for email templates.
 * Used by {@link de.tum.cit.aet.artemis.communication.service.notifications.MailService}
 * to avoid depending on the JPA entity directly.
 *
 * @param id   the data export ID
 * @param user nested DTO containing the login of the user who requested the export
 */
public record DataExportMailDTO(Long id, DataExportUserDTO user) {

    /**
     * Nested DTO for the user associated with a data export.
     *
     * @param login the login of the user
     */
    public record DataExportUserDTO(String login) {
    }

    /**
     * Creates a DataExportMailDTO from a DataExport entity.
     *
     * @param dataExport the data export entity
     * @return the DTO containing only the fields needed for email templates
     */
    public static DataExportMailDTO of(DataExport dataExport) {
        return new DataExportMailDTO(dataExport.getId(), new DataExportUserDTO(dataExport.getUser().getLogin()));
    }
}
