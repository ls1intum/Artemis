package de.tum.in.www1.artemis.web.rest.admin;

import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.export.DataExportService;
import de.tum.in.www1.artemis.web.rest.dto.RequestDataExportDTO;

/**
 * REST controller for requesting data exports for another user as admin.
 */
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
     * @return a DTO containing the id, the state and the request date of the data export
     */
    @PostMapping("data-exports/{login}")
    @EnforceAdmin
    public RequestDataExportDTO requestDataExportForUser(@PathVariable String login) {
        return dataExportService.requestDataExportForUserAsAdmin(login);
    }
}
