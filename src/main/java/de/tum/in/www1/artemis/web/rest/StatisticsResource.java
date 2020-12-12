package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.GraphType;
import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing user statistics.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsResource {

    private final Logger log = LoggerFactory.getLogger(StatisticsResource.class);

    private final StatisticsService service;

    public StatisticsResource(StatisticsService service) {
        this.service = service;
    }

    /**
     * GET management/statistics/data : get the amount of submissions made in the last "span" days.
     *
     * @param span the spantime of which the amount should be calculated
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @return the ResponseEntity with status 200 (OK) and the amount of submissions in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/data")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer[]> getChartData(@RequestParam SpanType span, @RequestParam Integer periodIndex, @RequestParam GraphType graphType) {
        log.debug("REST request to get amount of submission in the last {} days", span);
        return ResponseEntity.ok(this.service.getChartData(span, periodIndex, graphType));
    }

    /**
     * GET management/statistics/submissions : get the amount of submissions made in the last "span" days.
     *
     * @param span the spantime of which the amount should be calculated
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @return the ResponseEntity with status 200 (OK) and the amount of submissions in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/submissions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer[]> getTotalSubmissions(@RequestParam SpanType span, @RequestParam Integer periodIndex) {
        log.debug("REST request to get amount of submission in the last {} days", span);
        return ResponseEntity.ok(this.service.getTotalSubmissions(span, periodIndex));
    }

    /**
     * GET management/statistics/active-users : get the amount of active users in the last "span" days.
     *
     * @param span the spantime of which the amount should be calculated
     * @param periodIndex an index indicating which time period, 0 is current week, -1 is one week in the past, -2 is two weeks in the past ...
     * @return the ResponseEntity with status 200 (OK) and the amount of active users in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/active-users")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer[]> getActiveUsers(@RequestParam SpanType span, @RequestParam Integer periodIndex) {
        log.debug("REST request to get amount of submission in the last {} days", span);
        return ResponseEntity.ok(this.service.getActiveUsers(span, periodIndex));
    }

    /**
     * GET management/statistics/released-exercises : get the amount of released exercises in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of exercises in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/released-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer[]> getReleasedExercises(@RequestParam SpanType span, @RequestParam Integer periodIndex) {
        log.debug("REST request to get amount of released exercises in the last {} days", span);
        return ResponseEntity.ok(this.service.getReleasedExercises(span, periodIndex));
    }

}
