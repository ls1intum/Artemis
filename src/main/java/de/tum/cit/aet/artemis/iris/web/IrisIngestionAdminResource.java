package de.tum.cit.aet.artemis.iris.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.ActiveIngestionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.IngestionProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Admin REST endpoints for observing live lecture ingestion progress, backing the admin ingestion dashboard.
 */
@Lazy
@RestController
@Conditional(IrisEnabled.class)
@RequestMapping("api/iris/admin/")
public class IrisIngestionAdminResource {

    private static final Logger log = LoggerFactory.getLogger(IrisIngestionAdminResource.class);

    private final IngestionProgressService ingestionProgressService;

    public IrisIngestionAdminResource(IngestionProgressService ingestionProgressService) {
        this.ingestionProgressService = ingestionProgressService;
    }

    /**
     * GET api/iris/admin/lecture-ingestion/active : the lecture ingestions currently in flight, each with its live
     * per-step activity snapshot, for the admin ingestion dashboard.
     *
     * @return the active lecture ingestions
     */
    @GetMapping("lecture-ingestion/active")
    @EnforceAdmin
    @Operation(summary = "Active lecture ingestions", description = "Lecture ingestions currently in flight with their live per-step progress")
    @ApiResponse(responseCode = "200", description = "The active lecture ingestions")
    public ResponseEntity<List<ActiveIngestionDTO>> getActiveLectureIngestions() {
        log.debug("REST request to get the active lecture ingestions");
        return ResponseEntity.ok(ingestionProgressService.getActiveIngestions());
    }
}
