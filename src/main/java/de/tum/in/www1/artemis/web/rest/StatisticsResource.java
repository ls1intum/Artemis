package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
     * GET management/statistics/submissions : get the amount of submissions made in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of submissions in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/submissions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer[]> getTotalSubmissions(@RequestParam SpanType span, @RequestParam Integer periodIndex) {
        log.debug("REST request to get amount of submission in the last {} days", span);
        return ResponseEntity.ok(this.service.getTotalSubmissions(span, periodIndex));
    }

}
