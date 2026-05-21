package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.DataExportState;

/**
 * DTO for admin data export overview, including user information.
 *
 * @param id                   the id of the data export
 * @param userId               the id of the user who requested the export
 * @param userLogin            the login of the user
 * @param userName             the name of the user
 * @param dataExportState      the current state of the export
 * @param createdDate          when the export was requested
 * @param creationFinishedDate when the export creation was completed (null if not yet created)
 * @param downloadable         whether the export can be downloaded (file exists and state allows download)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DataExportAdminDTO(long id, Long userId, String userLogin, String userName, DataExportState dataExportState, ZonedDateTime createdDate,
        ZonedDateTime creationFinishedDate, boolean downloadable) {

    /**
     * Creates a DataExportAdminDTO from a DataExport entity.
     *
     * @param dataExport the data export entity
     * @return the DTO
     */
    public static DataExportAdminDTO of(DataExport dataExport) {
        var user = dataExport.getUser();
        boolean downloadable = dataExport.getFilePath() != null && dataExport.getDataExportState().isDownloadable();
        return new DataExportAdminDTO(dataExport.getId(), user != null ? user.getId() : null, user != null ? user.getLogin() : null, user != null ? user.getName() : null,
                dataExport.getDataExportState(), dataExport.getCreatedDate() != null ? dataExport.getCreatedDate().atZone(java.time.ZoneId.systemDefault()) : null,
                dataExport.getCreationFinishedDate(), downloadable);
    }
}
