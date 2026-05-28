package de.tum.cit.aet.artemis.iris.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardBreakdownDimension;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardBreakdownEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardConfigDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesDTO;
import de.tum.cit.aet.artemis.iris.service.IrisAdminDashboardService;

@RestController
@RequestMapping("api/iris/admin/dashboard/")
@Profile(PROFILE_CORE)
@Conditional(IrisEnabled.class)
@Lazy
public class IrisAdminDashboardResource {

    private static final String ENTITY_NAME = "irisDashboard";

    private final IrisAdminDashboardService dashboardService;

    private final IrisDashboardProperties properties;

    public IrisAdminDashboardResource(IrisAdminDashboardService dashboardService, IrisDashboardProperties properties) {
        this.dashboardService = dashboardService;
        this.properties = properties;
    }

    /**
     * GET /api/iris/admin/dashboard/overview : Get dashboard KPI overview.
     *
     * @param from start of the time window (inclusive)
     * @param to   end of the time window (exclusive)
     * @return the overview DTO
     */
    @GetMapping("overview")
    @EnforceAdmin
    public ResponseEntity<IrisDashboardOverviewDTO> getOverview(@RequestParam Instant from, @RequestParam Instant to) {
        validateWindow(from, to);
        return ResponseEntity.ok(dashboardService.computeOverview(from, to));
    }

    /**
     * GET /api/iris/admin/dashboard/time-series : Get time-series data for a metric.
     *
     * @param from   start of the time window (inclusive)
     * @param to     end of the time window (exclusive)
     * @param span   bucket span (DAY, WEEK, MONTH, QUARTER, YEAR)
     * @param metric the metric to compute
     * @return the time-series DTO
     */
    @GetMapping("time-series")
    @EnforceAdmin
    public ResponseEntity<IrisDashboardTimeSeriesDTO> getTimeSeries(@RequestParam Instant from, @RequestParam Instant to, @RequestParam String span,
            @RequestParam IrisDashboardMetric metric) {
        validateWindow(from, to);
        return ResponseEntity.ok(dashboardService.computeTimeSeries(from, to, span, metric));
    }

    /**
     * GET /api/iris/admin/dashboard/breakdown : Get breakdown data by dimension.
     *
     * @param from      start of the time window (inclusive)
     * @param to        end of the time window (exclusive)
     * @param dimension the breakdown dimension
     * @return the breakdown entries
     */
    @GetMapping("breakdown")
    @EnforceAdmin
    public ResponseEntity<List<IrisDashboardBreakdownEntryDTO>> getBreakdown(@RequestParam Instant from, @RequestParam Instant to,
            @RequestParam IrisDashboardBreakdownDimension dimension) {
        validateWindow(from, to);
        return ResponseEntity.ok(dashboardService.computeBreakdown(from, to, dimension));
    }

    /**
     * GET /api/iris/admin/dashboard/config : Get read-only dashboard configuration.
     *
     * @return the config DTO
     */
    @GetMapping("config")
    @EnforceAdmin
    public ResponseEntity<IrisDashboardConfigDTO> getConfig() {
        return ResponseEntity.ok(dashboardService.getConfig());
    }

    private void validateWindow(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new BadRequestAlertException("'from' must be before 'to'", ENTITY_NAME, "invalidWindow");
        }
        if (from.plus(Duration.ofDays(properties.getMaxQueryWindowDays())).isBefore(to)) {
            throw new BadRequestAlertException("Query window exceeds maximum of " + properties.getMaxQueryWindowDays() + " days", ENTITY_NAME, "windowTooLarge");
        }
        if (to.isAfter(de.tum.cit.aet.artemis.core.util.TimeUtil.now().toInstant().plus(Duration.ofDays(1)))) {
            throw new BadRequestAlertException("'to' cannot be more than 1 day in the future", ENTITY_NAME, "futureWindow");
        }
    }
}
