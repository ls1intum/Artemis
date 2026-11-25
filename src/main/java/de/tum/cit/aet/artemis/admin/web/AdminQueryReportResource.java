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
import de.tum.cit.aet.artemis.core.config.performance.QueryCollector;
import de.tum.cit.aet.artemis.core.config.performance.QueryReportDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * Admin REST endpoint for the runtime query detector: {@code GET /api/admin/performance/query-report} returns
 * the findings captured so far. Only active with both the {@code core} and the {@code e2e-performance} profile, so it has no impact on production.
 */
@Profile(PROFILE_CORE + " & " + SPRING_PROFILE_E2E_PERFORMANCE)
@EnforceAdmin
@FeatureUsage(UserFeature.MONITORING)
@Lazy
@RestController
@RequestMapping("api/admin/performance/")
public class AdminQueryReportResource {

    private static final Logger log = LoggerFactory.getLogger(AdminQueryReportResource.class);

    private final QueryCollector collector;

    private final IndexCatalog indexCatalog;

    public AdminQueryReportResource(QueryCollector collector, IndexCatalog indexCatalog) {
        this.collector = collector;
        this.indexCatalog = indexCatalog;
    }

    /**
     * Returns the findings collected since the server started.
     *
     * @return HTTP 200 with the report.
     */
    @GetMapping("query-report")
    public ResponseEntity<QueryReportDTO> getReport() {
        QueryReportDTO report = collector.getReport(indexCatalog.indexes());
        log.debug("[QueryReport] Report requested: {} repeated-query findings", report.repeatedQueries().size());
        return ResponseEntity.ok(report);
    }
}
