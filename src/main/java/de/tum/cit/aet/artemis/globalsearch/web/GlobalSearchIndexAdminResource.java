package de.tum.cit.aet.artemis.globalsearch.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.service.GlobalSearchIndexAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Admin REST endpoints backing the Course Content Ingestion Dashboard.
 * <p>
 * This first slice exposes a read-only Weaviate index overview (reachability plus per-collection object
 * counts). Per-course completeness, content manifests, retrigger, and the scheduler controls are added
 * as later slices.
 */
@Lazy
@RestController
@Conditional(WeaviateEnabled.class)
@RequestMapping("api/global-search/admin/")
public class GlobalSearchIndexAdminResource {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchIndexAdminResource.class);

    private final GlobalSearchIndexAdminService indexAdminService;

    public GlobalSearchIndexAdminResource(GlobalSearchIndexAdminService indexAdminService) {
        this.indexAdminService = indexAdminService;
    }

    /**
     * GET api/global-search/admin/index/overview : Weaviate reachability and per-collection object counts.
     *
     * @return the index overview snapshot
     */
    @GetMapping("index/overview")
    @EnforceAdmin
    @Operation(summary = "Weaviate index overview", description = "Weaviate reachability and per-collection object counts for the admin ingestion dashboard")
    @ApiResponse(responseCode = "200", description = "The index overview snapshot")
    public ResponseEntity<IndexOverviewDTO> getIndexOverview() {
        log.debug("REST request to get the Weaviate index overview");
        return ResponseEntity.ok(indexAdminService.getOverview());
    }
}
