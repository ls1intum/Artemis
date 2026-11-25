package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.performance.IndexCatalog;
import de.tum.cit.aet.artemis.core.config.performance.SlowQueryCollector;
import de.tum.cit.aet.artemis.core.config.performance.SlowQueryReportDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * Admin REST endpoint for the runtime repeated-query detector: {@code GET /api/admin/performance/slow-queries} returns
 * the findings captured so far. Only active with both the {@code core} and the {@code e2e-performance} profile, so it has no impact on production.
 */
@Profile(PROFILE_CORE + " & " + SPRING_PROFILE_E2E_PERFORMANCE)
@EnforceAdmin
@FeatureUsage(UserFeature.MONITORING)
@Lazy
@RestController
@RequestMapping("api/admin/performance/")
public class AdminSlowQueryReportResource {

    private static final Logger log = LoggerFactory.getLogger(AdminSlowQueryReportResource.class);

    private final SlowQueryCollector collector;

    private final IndexCatalog indexCatalog;

    public AdminSlowQueryReportResource(SlowQueryCollector collector, IndexCatalog indexCatalog) {
        this.collector = collector;
        this.indexCatalog = indexCatalog;
    }

    /**
     * Returns the findings collected since the server started.
     *
     * @return HTTP 200 with the report.
     */
    @GetMapping("slow-queries")
    public ResponseEntity<SlowQueryReportDTO> getReport() {
        SlowQueryReportDTO report = collector.getReport(indexCatalog.indexes());
        log.debug("[SlowQuery] Report requested: {} repeated-query findings", report.repeatedQueries().size());
        return ResponseEntity.ok(report);
    }
}
