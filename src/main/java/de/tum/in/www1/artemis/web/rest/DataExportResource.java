package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@RestController
@RequestMapping("/api")
public class DataExportResource {

    private final DataExportService dataExportService;

    private final UserRepository userRepository;

    public DataExportResource(DataExportService dataExportService, UserRepository userRepository) {
        this.dataExportService = dataExportService;
        this.userRepository = userRepository;
    }

    @PutMapping("/{userId}/data-export")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> requestDataExport(@PathVariable long userId) {
        User user = checkLoggedInUserIsAllowedToAccessDataExport(userId);
        try {
            dataExportService.requestDataExport(user);
        }
        catch (IOException e) {
            throw new InternalServerErrorException("Could not create data export");
        }
        return ResponseEntity.ok().build();

    }

    @GetMapping("/{userId}/data-export")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Resource> downloadDataExport(@PathVariable long userId) {
        checkLoggedInUserIsAllowedToAccessDataExport(userId);
        dataExportService.downloadDataExport();
        return ResponseEntity.ok().build();

    }

    private User checkLoggedInUserIsAllowedToAccessDataExport(long userId) {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        return userRepository.getUserWithGroupsAndAuthorities(currentUserLogin.get());
        // .ifPresentOrElse(user -> {
        //
        // if (user.getId() != userId) {
        // throw new AccessForbiddenException("You are not allowed to access this resource");
        // }
        // }, () -> {
        // throw new AccessForbiddenException("You are not allowed to access this resource");
        // });

    }
}
