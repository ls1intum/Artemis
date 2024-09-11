package de.tum.cit.aet.artemis.web.rest.admin;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.enumeration.GraphType;
import de.tum.cit.aet.artemis.domain.enumeration.SpanType;
import de.tum.cit.aet.artemis.domain.enumeration.StatisticsView;
import de.tum.cit.aet.artemis.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.service.StatisticsService;

/**
 * REST controller for administrating statistics.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminStatisticsResource {

    private static final Logger log = LoggerFactory.getLogger(AdminStatisticsResource.class);

    private final StatisticsService statisticsService;

    public AdminStatisticsResource(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * GET admin/management/statistics/data : get the graph data in the last "span" days in the given period.
     *
     * @param span        the spanTime of which the amount should be calculated
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @param graphType   the type of graph the data should be fetched
     * @return the ResponseEntity with status 200 (OK) and the data in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/data")
    @EnforceAdmin
    public ResponseEntity<List<Integer>> getChartData(@RequestParam SpanType span, @RequestParam Integer periodIndex, @RequestParam GraphType graphType) {
        log.debug("REST request to get graph data");
        return ResponseEntity.ok(this.statisticsService.getChartData(span, periodIndex, graphType, StatisticsView.ARTEMIS, null));
    }
}
