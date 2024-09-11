package de.tum.cit.aet.artemis.web.rest.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.service.export.DataExportService;
import de.tum.cit.aet.artemis.web.rest.dto.RequestDataExportDTO;

/**
 * REST controller for requesting data exports for another user as admin.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminDataExportResource {

    private final DataExportService dataExportService;

    public AdminDataExportResource(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }

    /**
     * Request a data export for the given user as admin
     *
     * @param login the login of the user to request the data export for
     * @return the ResponseEntity with status 200 (OK) and with body a DTO containing the id, the state and the request date of the data export
     */
    @PostMapping("data-exports/{login}")
    @EnforceAdmin
    public ResponseEntity<RequestDataExportDTO> requestDataExportForUser(@PathVariable String login) {
        return ResponseEntity.ok(dataExportService.requestDataExportForUserAsAdmin(login));
    }
}
