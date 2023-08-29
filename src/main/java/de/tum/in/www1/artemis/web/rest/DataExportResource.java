package de.tum.in.www1.artemis.web.rest;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.dataexport.DataExportService;
import de.tum.in.www1.artemis.web.rest.dto.DataExportDTO;
import de.tum.in.www1.artemis.web.rest.dto.RequestDataExportDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for data exports
 */
@RestController
@RequestMapping("api/")
public class DataExportResource {

    private final int DAYS_BETWEEN_DATA_EXPORTS;

    private final DataExportService dataExportService;

    private final DataExportRepository dataExportRepository;

    private final UserRepository userRepository;

    public DataExportResource(@Value("${artemis.data-export.days-between-data-exports:14}") int daysBetweenDataExports, DataExportService dataExportService,
            DataExportRepository dataExportRepository, UserRepository userRepository) {
        this.DAYS_BETWEEN_DATA_EXPORTS = daysBetweenDataExports;
        this.dataExportService = dataExportService;
        this.dataExportRepository = dataExportRepository;
        this.userRepository = userRepository;
    }

    /**
     * Request a data export for the given user
     *
     * @return the data export object
     */
    @PostMapping("data-exports")
    @EnforceAtLeastStudent
    public RequestDataExportDTO requestDataExport() {
        if (!canRequestDataExport()) {
            throw new AccessForbiddenException("You can only request a data export every " + DAYS_BETWEEN_DATA_EXPORTS + " days");
        }
        return dataExportService.requestDataExport();
    }

    /**
     * Checks if the user can request a new data export.
     *
     * @return true if the user can request a new data export, false otherwise
     */
    private boolean canRequestDataExport() {
        var user = userRepository.getUser();
        var dataExports = dataExportRepository.findAllDataExportsByUserId(user.getId());
        if (dataExports.isEmpty()) {
            return true;
        }
        var latestDataExport = dataExports.stream().max(Comparator.comparing(DataExport::getCreatedDate)).get();
        var olderThanDaysBetweenDataExports = Duration.between(latestDataExport.getCreatedDate().atZone(ZoneId.systemDefault()), ZonedDateTime.now())
                .toDays() >= DAYS_BETWEEN_DATA_EXPORTS;

        return olderThanDaysBetweenDataExports || latestDataExport.getDataExportState() == DataExportState.FAILED;
    }

    /**
     * Download the data export for the given user
     *
     * @param dataExportId the id of the data export to download
     * @return A resource containing the data export zip file
     */
    @GetMapping("data-exports/{dataExportId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Resource> downloadDataExport(@PathVariable long dataExportId) {
        DataExport dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        checkDataExportCanBeDownloaded(dataExport);
        Resource resource = dataExportService.downloadDataExport(dataExport);
        File finalZipFile = Path.of(dataExport.getFilePath()).toFile();
        return ResponseEntity.ok().contentLength(finalZipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", finalZipFile.getName()).body(resource);
    }

    private void checkDataExportCanBeDownloaded(DataExport dataExport) {
        if (!dataExport.getDataExportState().isDownloadable()) {
            throw new AccessForbiddenException("Data export has either not been created or already been deleted");
        }
    }

    /**
     * checks if the currently logged-in user is the owner of the given data export
     *
     * @param dataExport the data export that needs to be checked
     * @throws AccessForbiddenException if logged-in user isn't the owner of the data export
     */
    private void currentlyLoggedInUserIsOwnerOfDataExportElseThrow(@NotNull DataExport dataExport) {
        if (!currentlyLoggedInUserIsOwnerOfDataExport(dataExport)) {
            throw new AccessForbiddenException("data export", dataExport.getId());
        }
    }

    /**
     * checks if the currently logged-in user is owner of the given data export
     *
     * @param dataExport the data export that needs to be checked
     * @return true if the user is the owner of the data export, false otherwise
     */
    private boolean currentlyLoggedInUserIsOwnerOfDataExport(DataExport dataExport) {
        if (dataExport.getUser() == null) {
            return false;
        }
        else {
            return dataExport.getUser().getLogin().equals(userRepository.getUser().getLogin());
        }
    }

    /**
     * Check if the user can request a data export
     *
     * @return true if the user can request a data export, false otherwise
     */
    @GetMapping("data-exports/can-request")
    @EnforceAtLeastStudent
    public boolean canRequestExport() {
        return canRequestDataExport();
    }

    /**
     * Check if the user can download any data export
     *
     * @return a data export DTO with the id of the export that can be downloaded or a DTO with a id of null if no export can be downloaded
     */
    @GetMapping("data-exports/can-download")
    @EnforceAtLeastStudent
    public DataExportDTO canDownloadAnyExport() {
        return dataExportService.canDownloadAnyDataExport();
    }

    /**
     * Check if the user can download a specific data export
     *
     * @param dataExportId the id of the data export that should be checked
     * @return true if the user can download the data export, false otherwise
     */
    @GetMapping("data-exports/{dataExportId}/can-download")
    @EnforceAtLeastStudent
    public boolean canDownloadSpecificExport(@PathVariable long dataExportId) {
        return canDownloadSpecificDataExport(dataExportId);
    }

    /**
     * Checks if the data export with the given id can be downloaded.
     *
     * @param dataExportId the id of the data export to check
     * @return true if the data export can be downloaded, false otherwise
     * @throws de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException  if the data export or the user could not be found
     * @throws de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException if the user is not allowed to download the data export
     */
    private boolean canDownloadSpecificDataExport(long dataExportId) throws EntityNotFoundException, AccessForbiddenException {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        return dataExport.getDataExportState().isDownloadable();
    }

}
