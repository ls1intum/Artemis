package de.tum.cit.aet.artemis.iris.web.admin;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardBreakdownDimension;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardBreakdownEntryDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardConfigDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardMetric;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardOverviewDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardSpan;
import de.tum.cit.aet.artemis.iris.dto.IrisDashboardTimeSeriesDTO;
import de.tum.cit.aet.artemis.iris.service.IrisAdminDashboardService;

@Conditional(IrisEnabled.class)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/iris/admin/dashboard")
public class IrisAdminDashboardResource {

    private static final Logger log = LoggerFactory.getLogger(IrisAdminDashboardResource.class);

    private final IrisAdminDashboardService irisAdminDashboardService;

    public IrisAdminDashboardResource(IrisAdminDashboardService irisAdminDashboardService) {
        this.irisAdminDashboardService = irisAdminDashboardService;
    }

    @GetMapping("overview")
    public ResponseEntity<IrisDashboardOverviewDTO> getOverview(@RequestParam Instant from, @RequestParam Instant to, @RequestParam(required = false) @Nullable String chatMode) {
        log.debug("REST request to get Iris admin dashboard overview from {} to {} for chat mode {}", from, to, chatMode);
        return ResponseEntity.ok(irisAdminDashboardService.getOverview(from, to, chatMode));
    }

    @GetMapping("time-series")
    public ResponseEntity<IrisDashboardTimeSeriesDTO> getTimeSeries(@RequestParam Instant from, @RequestParam Instant to, @RequestParam IrisDashboardSpan span,
            @RequestParam IrisDashboardMetric metric, @RequestParam(required = false) @Nullable String chatMode) {
        log.debug("REST request to get Iris admin dashboard time series {} from {} to {} with span {} for chat mode {}", metric, from, to, span, chatMode);
        return ResponseEntity.ok(irisAdminDashboardService.getTimeSeries(from, to, span, metric, chatMode));
    }

    @GetMapping("breakdown")
    public ResponseEntity<List<IrisDashboardBreakdownEntryDTO>> getBreakdown(@RequestParam Instant from, @RequestParam Instant to,
            @RequestParam IrisDashboardBreakdownDimension dimension, @RequestParam(required = false) @Nullable String chatMode) {
        log.debug("REST request to get Iris admin dashboard breakdown {} from {} to {} for chat mode {}", dimension, from, to, chatMode);
        return ResponseEntity.ok(irisAdminDashboardService.getBreakdown(from, to, dimension, chatMode));
    }

    @GetMapping("config")
    public ResponseEntity<IrisDashboardConfigDTO> getConfig() {
        log.debug("REST request to get Iris admin dashboard config");
        return ResponseEntity.ok(irisAdminDashboardService.getConfig());
    }
}
