package de.tum.in.www1.artemis.web.rest;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@RestController
@RequestMapping("api/")
public class DataExportResource {

    private final DataExportService dataExportService;

    private final DataExportRepository dataExportRepository;

    private final Logger log = LoggerFactory.getLogger(DataExportResource.class);

    public DataExportResource(DataExportService dataExportService, DataExportRepository dataExportRepository) {
        this.dataExportService = dataExportService;
        this.dataExportRepository = dataExportRepository;
    }

    /**
     * Request a data export for the given user
     *
     * @return the data export object
     */
    @PutMapping("data-export")
    @EnforceAtLeastStudent
    public DataExport requestDataExport() {
        // in the follow-ups, creating a data export will be a scheduled operation, therefore we split the endpoints for requesting and downloading
        // for now we return the data export object, so the client can make the request to download the export.

        try {
            return dataExportService.requestDataExport();
        }
        catch (Exception e) {
            log.error("Could not create data export", e);
            throw new InternalServerErrorException("Could not create data export:" + e.getMessage());
        }

    }

    /**
     * Download the data export for the given user
     *
     * @param dataExportId the id of the data export to download
     * @return A resource containing the data export zip file
     * @throws de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException  if the data export could not be found
     * @throws de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException if the user is not allowed to download the data export
     */
    @GetMapping("data-export/{dataExportId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Resource> downloadDataExport(@PathVariable long dataExportId) {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        currentlyLoggedInUserIsOwnerOfDataExportElseThrow(dataExport);
        checkDataExportCanBeDownloaded(dataExport);
        var dataExportPath = dataExportService.downloadDataExport(dataExport);
        var finalZipFile = dataExportPath.toFile();
        InputStreamResource resource;
        try {
            resource = new InputStreamResource(new FileInputStream(finalZipFile));
        }
        catch (FileNotFoundException e) {
            log.error("Could not find data export file", e);
            throw new InternalServerErrorException("Could not find data export file");
        }
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
    public void currentlyLoggedInUserIsOwnerOfDataExportElseThrow(@NotNull DataExport dataExport) {
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
            return dataExport.getUser().getLogin().equals(SecurityUtils.getCurrentUserLogin().get());
        }
    }
}
