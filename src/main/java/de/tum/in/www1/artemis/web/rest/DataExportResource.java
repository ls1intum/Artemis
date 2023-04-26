package de.tum.in.www1.artemis.web.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@RestController
@RequestMapping("/api")
public class DataExportResource {

    private final DataExportService dataExportService;

    private final Logger log = LoggerFactory.getLogger(DataExportService.class);

    private final UserRepository userRepository;

    public DataExportResource(DataExportService dataExportService, UserRepository userRepository) {
        this.dataExportService = dataExportService;
        this.userRepository = userRepository;
    }

    @PutMapping("/{userId}/data-export")
    @PreAuthorize("hasRole('USER')")
    public DataExport requestDataExport(@PathVariable long userId) {
        // in the follow-ups, creating a data export will be a scheduled operation, therefore we split the endpoints for requesting and downloading
        // for now we return the data export object, so the client can make the request to download the export.
        var user = userRepository.findOneWithGroupsAndAuthoritiesByIdOrElseThrow(userId);

        try {
            return dataExportService.requestDataExport(user);
        }
        catch (Exception e) {
            throw new InternalServerErrorException("Could not create data export");
        }

    }

    @GetMapping("/{userId}/data-export/{dataExportId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Resource> downloadDataExport(@PathVariable long userId, @PathVariable long dataExportId) {
        var dataExport = dataExportService.downloadDataExport(userId, dataExportId);
        var finalZipFile = new File(dataExport.getFilePath());
        InputStreamResource resource;
        try {
            resource = new InputStreamResource(new FileInputStream(finalZipFile));
        }
        catch (FileNotFoundException e) {
            throw new InternalServerErrorException("Could not find data export file");
        }
        return ResponseEntity.ok().contentLength(finalZipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", finalZipFile.getName()).body(resource);

    }

}
