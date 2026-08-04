package de.tum.cit.aet.artemis.iris.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.ActiveIngestionDTO;
import de.tum.cit.aet.artemis.iris.dto.PyrisReachabilityDTO;
import de.tum.cit.aet.artemis.iris.dto.RecentIngestionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.IngestionProgressService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
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

    private final PyrisHealthIndicator pyrisHealthIndicator;

    public IrisIngestionAdminResource(IngestionProgressService ingestionProgressService, PyrisHealthIndicator pyrisHealthIndicator) {
        this.ingestionProgressService = ingestionProgressService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
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
        // Refresh Iris health (cached): a detected DOWN -> UP transition deterministically fails any ingestion that was
        // in flight across an Iris restart and moves it into the recent history, so the dashboard stays truthful.
        pyrisHealthIndicator.health(true);
        return ResponseEntity.ok(ingestionProgressService.getActiveIngestions());
    }

    /**
     * GET api/iris/admin/lecture-ingestion/recent : the most recently finished or failed lecture ingestions, each with
     * its full per-step timeline and, for failures, the step it failed at and the error, for the admin dashboard.
     *
     * @return the recent lecture ingestions, newest first
     */
    @GetMapping("lecture-ingestion/recent")
    @EnforceAdmin
    @Operation(summary = "Recent lecture ingestions", description = "Recently finished or failed lecture ingestions with per-step durations and failure details")
    @ApiResponse(responseCode = "200", description = "The recent lecture ingestions")
    public ResponseEntity<List<RecentIngestionDTO>> getRecentLectureIngestions() {
        log.debug("REST request to get the recent lecture ingestions");
        return ResponseEntity.ok(ingestionProgressService.getRecentIngestions());
    }

    /**
     * GET api/iris/admin/pyris-health : whether the Iris service is currently reachable, for the dashboard health tile.
     * When Iris is down, dispatched ingestions cannot run, so surfacing this explains an otherwise silent dashboard.
     *
     * @return the Iris reachability
     */
    @GetMapping("pyris-health")
    @EnforceAdmin
    @Operation(summary = "Iris reachability", description = "Whether the Iris service is currently reachable, for the ingestion dashboard health tile")
    @ApiResponse(responseCode = "200", description = "The Iris reachability")
    public ResponseEntity<PyrisReachabilityDTO> getPyrisHealth() {
        log.debug("REST request to get the Iris reachability");
        boolean reachable = pyrisHealthIndicator.health(true).getStatus() == Status.UP;
        return ResponseEntity.ok(new PyrisReachabilityDTO(reachable));
    }
}
