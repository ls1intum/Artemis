package de.tum.cit.aet.artemis.globalsearch.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.web.util.PaginationUtil.generatePaginationHttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateHealthIndicator;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedCollectionCountDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionCoverageDTO;
import de.tum.cit.aet.artemis.globalsearch.service.CoverageRecomputeService;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService;
import de.tum.cit.aet.artemis.iris.api.IrisHealthApi;

/**
 * Admin-only, read-only endpoints for the ingestion-observability dashboard: the index overview, the stored per-course
 * coverage projection (cross-course views), a live-per-page coverage view (default matrix view), and a manual refresh.
 * Only available when Weaviate is enabled.
 * <p>
 * TEMPORARY (revert before merge): normally admin-only under {@code api/global-search/admin/} with a class-level
 * {@code @EnforceAdmin}. Relaxed to instructor and moved out of the {@code /admin/} segment so the page can be
 * exercised on the test server without an admin account: {@code SecurityConfiguration} maps every per-module admin
 * path to {@code ROLE_ADMIN} in the filter chain, ahead of any method-level annotation.
 */
@Profile(PROFILE_CORE)
@Conditional(WeaviateEnabled.class)
@Lazy
@RestController
@RequestMapping("api/global-search/ingestion-dashboard/")
public class IngestionCoverageResource {

    /** The Iris content collections shown in the overview, addressed by their exact (unprefixed) names. */
    private static final List<String> IRIS_CONTENT_COLLECTIONS = List.of(IngestionCoverageWeaviateReadService.LECTURES_COLLECTION,
            IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION, IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION,
            IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION);

    private final WeaviateHealthIndicator weaviateHealthIndicator;

    private final IngestionCoverageWeaviateReadService weaviateReadService;

    private final CoverageRecomputeService coverageRecomputeService;

    private final ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    private final Environment environment;

    private final Optional<IrisHealthApi> irisHealthApi;

    public IngestionCoverageResource(WeaviateHealthIndicator weaviateHealthIndicator, IngestionCoverageWeaviateReadService weaviateReadService,
            CoverageRecomputeService coverageRecomputeService, Environment environment, Optional<IrisHealthApi> irisHealthApi) {
        this.weaviateHealthIndicator = weaviateHealthIndicator;
        this.weaviateReadService = weaviateReadService;
        this.coverageRecomputeService = coverageRecomputeService;
        this.environment = environment;
        this.irisHealthApi = irisHealthApi;
    }

    /**
     * GET .../index/overview : Weaviate reachability + address, whether Iris is enabled, and the live object count of each
     * tracked collection (a collection that cannot be read is reported unavailable, not an error).
     *
     * @return the index overview
     */
    @EnforceAtLeastInstructor
    @GetMapping("index/overview")
    public ResponseEntity<IndexOverviewDTO> getIndexOverview() {
        Health health = weaviateHealthIndicator.health();
        boolean reachable = health.getStatus() == Status.UP;
        String address = String.valueOf(health.getDetails().get("Address"));
        boolean irisEnabled = artemisConfigHelper.isIrisEnabled(environment);
        // Enabled says the module is switched on; reachable says Iris answered. Reporting only the former reads as
        // healthy while Iris is down, which is the state an admin most needs to see.
        boolean irisReachable = irisHealthApi.map(IrisHealthApi::isReachable).orElse(false);

        List<IndexedCollectionCountDTO> collections = new ArrayList<>();
        collections.add(toCountDto(SearchableEntitySchema.COLLECTION_NAME, weaviateReadService.countPrefixedCollection(SearchableEntitySchema.COLLECTION_NAME)));
        for (String collection : IRIS_CONTENT_COLLECTIONS) {
            collections.add(toCountDto(collection, weaviateReadService.countExternalCollection(collection)));
        }
        return ResponseEntity.ok(new IndexOverviewDTO(reachable, address, irisEnabled, irisReachable, collections));
    }

    /**
     * GET .../coverage : the stored per-course coverage projection for the cross-course matrix views (worst-first,
     * release-date, most-recent-ingestion, status/active filter), paginated and sorted on the projection columns and
     * optionally narrowed by a course-title search. Serves the stored table instantly and triggers a background recompute
     * when it is stale (stale-while-revalidate).
     *
     * @param status   an optional status to filter by
     * @param active   an optional active/inactive course filter
     * @param search   an optional case-insensitive course-title search, applied alongside the filters rather than
     *                     instead of them
     * @param pageable the page and sort
     * @return the requested page of stored coverage rows
     */
    @EnforceAtLeastInstructor
    @GetMapping("coverage")
    public ResponseEntity<List<IngestionCoverageDTO>> getStoredCoverage(@RequestParam(required = false) IngestionCoverageStatus status,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String search, Pageable pageable) {
        // Cross-bean call so the @Async recompute actually runs off the request thread.
        coverageRecomputeService.triggerRecomputeIfStale();
        Page<IngestionCoverageDTO> page = coverageRecomputeService.readStoredCoverage(status, active, search, pageable);
        HttpHeaders headers = generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET .../coverage/page : the default matrix view - coverage computed LIVE for just the visible page of courses,
     * selected by DB-native sort and an optional title search. Always fresh; never reads the stored projection.
     *
     * @param search   an optional case-insensitive course-title search
     * @param pageable the page and sort (on course columns)
     * @return the requested page of live-computed coverage
     */
    @EnforceAtLeastInstructor
    @GetMapping("coverage/page")
    public ResponseEntity<List<IngestionCoverageDTO>> getLiveCoveragePage(@RequestParam(required = false) String search, Pageable pageable) {
        Page<IngestionCoverageDTO> page = coverageRecomputeService.computeLiveCoveragePage(search, pageable);
        HttpHeaders headers = generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * POST .../coverage/refresh : forces a background recompute of the whole projection (backs the "Refresh data"
     * button), lease-guarded so it is a no-op if one is already running.
     *
     * @return 200 once the recompute has been triggered
     */
    @EnforceAtLeastInstructor
    @PostMapping("coverage/refresh")
    public ResponseEntity<Void> refreshCoverage() {
        coverageRecomputeService.forceRecompute();
        return ResponseEntity.ok().build();
    }

    private static IndexedCollectionCountDTO toCountDto(String collection, OptionalLong count) {
        return count.isPresent() ? IndexedCollectionCountDTO.of(collection, count.getAsLong()) : IndexedCollectionCountDTO.unavailable(collection);
    }
}
