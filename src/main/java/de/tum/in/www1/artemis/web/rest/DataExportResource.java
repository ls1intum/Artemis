package de.tum.in.www1.artemis.web.rest;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.export.DataExportService;
import de.tum.in.www1.artemis.web.rest.dto.DataExportDTO;
import de.tum.in.www1.artemis.web.rest.dto.RequestDataExportDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for data exports.
 * It contains the REST endpoints for requesting, downloading data exports and checking if a data export can be requested or downloaded.
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
     * POST /data-exports: Request a data export for the currently logged-in user.
     *
     * @return a DTO containing the id of the data export that was created, its state and when it was requested
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
     * <p>
     * This is the case if the user has not requested a data export yet or if the last data export was created more than DAYS_BETWEEN_DATA_EXPORTS days ago.
     *
     * @return true if the user can request a new data export, false otherwise
     */
    private boolean canRequestDataExport() {
        var user = userRepository.getUser();
        var dataExports = dataExportRepository.findAllDataExportsByUserIdOrderByRequestDateDesc(user.getId());
        if (dataExports.isEmpty()) {
            return true;
        }
        // because we order by request date desc, the first data export is the latest one
        var latestDataExport = dataExports.get(0);
        var olderThanDaysBetweenDataExports = Duration.between(latestDataExport.getCreatedDate().atZone(ZoneId.systemDefault()), ZonedDateTime.now())
                .toDays() >= DAYS_BETWEEN_DATA_EXPORTS;

        return olderThanDaysBetweenDataExports || latestDataExport.getDataExportState() == DataExportState.FAILED;
    }

    /**
     * GET /data-exports/{dataExportId}: Download the data export for the given id.
     * <p>
     * We check if the user is the owner of the data export and if the data export can be downloaded.
     * If this is the case, we return a resource containing the data export zip file.
     * The file name is set to the name of the zip file.
     * The content disposition header is set to attachment so that the browser will download the file instead of displaying it.
     *
     * @param dataExportId the id of the data export to download
     * @return A resource containing the data export zip file
     */
    @GetMapping("data-exports/{dataExportId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Resource> downloadDataExport(@PathVariable long dataExportId) {
        DataExport dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        dataExportService.checkDataExportCanBeDownloadedElseThrow(dataExport);
        Resource resource = dataExportService.downloadDataExport(dataExport);
        File finalZipFile = Path.of(dataExport.getFilePath()).toFile();
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment").filename(finalZipFile.getName()).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        return ResponseEntity.ok().contentLength(finalZipFile.length()).headers(headers).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", finalZipFile.getName())
                .body(resource);
    }

    /**
     * Checks if the currently logged-in user is the owner of the given data export.
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
     * Checks if the currently logged-in user is owner of the given data export.
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
     * GET /data-exports/can-request: Check if the logged-in user can request a data export.
     *
     * @return true if the user can request a data export, false otherwise
     */
    @GetMapping("data-exports/can-request")
    @EnforceAtLeastStudent
    public boolean canRequestExport() {
        return canRequestDataExport();
    }

    /**
     * GET /data-exports/can-download: Check if the logged-in user can download any data export.
     *
     * @return a data export DTO with the id of the export that can be downloaded or a DTO with an id of null if no export can be downloaded
     */
    @GetMapping("data-exports/can-download")
    @EnforceAtLeastStudent
    public DataExportDTO canDownloadAnyExport() {
        return dataExportService.canDownloadAnyDataExport();
    }

    /**
     * GET /data-exports/{dataExportId}/can-download: Check if the logged-in user can download the data export with the given id.
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
     * @throws EntityNotFoundException  if the data export or the user could not be found
     * @throws AccessForbiddenException if the user is not allowed to download the data export
     */
    private boolean canDownloadSpecificDataExport(long dataExportId) throws EntityNotFoundException, AccessForbiddenException {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        return dataExport.getDataExportState().isDownloadable();
    }

}
