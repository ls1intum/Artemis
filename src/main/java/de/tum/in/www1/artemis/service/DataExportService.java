package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.DataExportDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service Implementation for managing the data export in accordance with Art. 15 GDPR.
 */
@Service
public class DataExportService {

    private static final int DAYS_BETWEEN_DATA_EXPORTS = 14;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final DataExportRepository dataExportRepository;

    private final FileService fileService;

    public DataExportService(UserRepository userRepository, AuthorizationCheckService authorizationCheckService, DataExportRepository dataExportRepository,
            FileService fileService) {
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.dataExportRepository = dataExportRepository;
        this.fileService = fileService;
    }

    /**
     * Requests a data export for the given user.
     * This will create a new DataExport object in the database and start the creation of the data export.
     *
     * @return the created DataExport object
     */
    public DataExport requestDataExport() throws IOException {
        if (!canRequestDataExport()) {
            throw new AccessForbiddenException("You can only request a data export every " + DAYS_BETWEEN_DATA_EXPORTS + " days");
        }
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        User user = userRepository.getUser();
        dataExport.setUser(user);
        dataExport.setRequestDate(ZonedDateTime.now());
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;
    }

    /**
     * Download the data export for the given data export id.
     *
     * @param dataExportId the id of the data export to download
     * @return the file path where the data export is stored
     * @throws de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException  if the data export or the user could not be found
     * @throws de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException if the user is not allowed to download the data export
     */
    public Path downloadDataExport(long dataExportId) {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        authorizationCheckService.currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        checkDataExportCanBeDownloaded(dataExport);

        dataExport.setDownloadDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport = dataExportRepository.save(dataExport);
        return Path.of(dataExport.getFilePath());
    }

    private void checkDataExportCanBeDownloaded(DataExport dataExport) {
        if (!dataExport.getDataExportState().isDownloadable()) {
            throw new AccessForbiddenException("Data export has either not been created or already been deleted");
        }
    }

    /**
     * Checks if the user can request a new data export.
     *
     * @return true if the user can request a new data export, false otherwise
     */
    public boolean canRequestDataExport() {
        var user = userRepository.getUserWithDataExports();
        var latestDataExport = user.getDataExports().stream().max(Comparator.comparing(DataExport::getRequestDate));
        if (latestDataExport.isEmpty()) {
            return true;
        }
        var latestDataExportCreationDate = latestDataExport.get().getRequestDate();
        // allow requesting a new data export if the latest data export is older than 14 days or its creation has failed
        return Duration.between(latestDataExportCreationDate, ZonedDateTime.now()).toDays() >= DAYS_BETWEEN_DATA_EXPORTS || latestDataExport.get().getDataExportState().hasFailed();
    }

    /**
     * Checks if the user can download any data export.
     *
     * @return a DataExportDTO containing the id of the data export to download or null if no data export can be downloaded
     */
    public DataExportDTO canDownloadAnyDataExport() {
        var noDataExport = new DataExportDTO(null, null);
        var user = userRepository.getUserWithDataExports();
        var latestDataExportOptional = user.getDataExports().stream().max(Comparator.comparing(DataExport::getRequestDate));
        if (latestDataExportOptional.isEmpty()) {
            return noDataExport;
        }
        var latestDataExport = latestDataExportOptional.get();
        // either the latest data export is downloadable or none of the data exports are
        if (latestDataExport.getDataExportState().isDownloadable()) {
            return new DataExportDTO(latestDataExport.getId(), latestDataExport.getDataExportState());
        }
        else {
            return new DataExportDTO(null, latestDataExport.getDataExportState());
        }
    }

    /**
     * Checks if the data export with the given id can be downloaded.
     *
     * @param dataExportId the id of the data export to check
     * @return true if the data export can be downloaded, false otherwise
     * @throws de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException  if the data export or the user could not be found
     * @throws de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException if the user is not allowed to download the data export
     */
    public boolean canDownloadSpecificDataExport(long dataExportId) {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        authorizationCheckService.currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        return dataExport.getDataExportState().isDownloadable();
    }

    /**
     * Deletes the given data export and sets the state to DELETED or DOWNLOADED_DELETED depending on whether the export has been downloaded or not.
     *
     * @param dataExport the data export to delete
     */
    public void deleteDataExportAndSetDataExportState(DataExport dataExport) {
        if (dataExport.getFilePath() == null) {
            return;
        }
        fileService.scheduleForDirectoryDeletion(Path.of(dataExport.getFilePath()), 2);
        if (dataExport.getDataExportState().hasBeenDownloaded()) {
            dataExport.setDataExportState(DataExportState.DOWNLOADED_DELETED);
        }
        else {
            dataExport.setDataExportState(DataExportState.DELETED);
        }
        dataExportRepository.save(dataExport);
    }

}
