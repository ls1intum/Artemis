package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.performance.SlowQueryCollector;
import de.tum.cit.aet.artemis.core.config.performance.SlowQueryReportDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;

/**
 * Admin REST endpoint for the runtime slow-query detector.
 * <p>
 * Exposes two operations:
 * <ul>
 * <li>{@code GET  /api/core/admin/performance/slow-queries} – returns the current JSON report
 * of all slow queries and N+1 suspects captured since the last reset.</li>
 * <li>{@code POST /api/core/admin/performance/slow-queries/reset} – clears all accumulated
 * data, allowing a fresh collection window to start.</li>
 * </ul>
 * <p>
 * Both operations require {@code ROLE_ADMIN}. In CI this is satisfied by the Artemis admin
 * account configured via {@code ARTEMIS_USERMANAGEMENT_INTERNALADMIN_*} environment variables.
 * <p>
 * Only active when <em>both</em> the {@code core} module profile and the {@code e2e-performance}
 * profile are enabled. The endpoint therefore has zero impact on production deployments.
 */
@Profile(PROFILE_CORE + " & " + SPRING_PROFILE_E2E_PERFORMANCE)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/core/admin/performance")
public class AdminSlowQueryReportResource {

    private static final Logger log = LoggerFactory.getLogger(AdminSlowQueryReportResource.class);

    private final SlowQueryCollector collector;

    public AdminSlowQueryReportResource(SlowQueryCollector collector) {
        this.collector = collector;
    }

    /**
     * Returns a snapshot of all slow-query findings collected since the server started
     * (or since the last {@code /reset} call).
     *
     * @return HTTP 200 with the {@link SlowQueryReportDTO} as JSON body.
     */
    @GetMapping("slow-queries")
    public ResponseEntity<SlowQueryReportDTO> getReport() {
        SlowQueryReportDTO report = collector.getReport();
        log.debug("[SlowQuery] Report requested: {} slow queries, {} N+1 suspects", report.slowQueryCount(), report.n1SuspectCount());
        return ResponseEntity.ok(report);
    }

    /**
     * Clears all accumulated slow-query data.
     * Useful for separating collection windows within a single server lifetime.
     *
     * @return HTTP 200 with an empty body.
     */
    @PostMapping("slow-queries/reset")
    public ResponseEntity<Void> reset() {
        collector.reset();
        log.info("[SlowQuery] Collector reset via admin endpoint");
        return ResponseEntity.ok().build();
    }
}
