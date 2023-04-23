package de.tum.in.www1.artemis.web.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
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
        // for now we return the id, so the client can make the request to download the export.
        // in the future, we will return nothing other than a successful status because the url to retrieve the data export will be part of the email and the client doesn't
        // need to be able to construct it.
        User user = checkLoggedInUserIsAllowedToAccessDataExport(userId);
        DataExport dataExport;
        try {
            dataExport = dataExportService.requestDataExport(user);
        }
        catch (IOException e) {
            throw new InternalServerErrorException("Could not create data export");
        }
        return dataExport;

    }

    @GetMapping("/{userId}/data-export/{dataExportId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Resource> downloadDataExport(@PathVariable long userId, @PathVariable long dataExportId) throws FileNotFoundException {
        checkLoggedInUserIsAllowedToAccessDataExport(userId);
        var dataExport = dataExportService.downloadDataExport(userId, dataExportId);
        var finalZipFile = new File(dataExport.getFilePath());
        InputStreamResource resource = new InputStreamResource(new FileInputStream(finalZipFile));

        return ResponseEntity.ok().contentLength(finalZipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", finalZipFile.getName()).body(resource);

    }

    private User checkLoggedInUserIsAllowedToAccessDataExport(long userId) {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isEmpty()) {
            throw new AccessForbiddenException("Cannot determine currently logged user, hence access is forbidden");
        }

        var user = userRepository.getUserWithGroupsAndAuthorities(currentUserLogin.get());

        if (user.getId() != userId) {
            throw new AccessForbiddenException("User id of data export and id of currently logged in user do not match");
        }
        return user;
    }
}
