package de.tum.in.www1.artemis.web.rest.science;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.science.ScienceEventType;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.science.ScienceEventService;

/**
 * REST controller providing the science related endpoints.
 */
@RestController
@RequestMapping("api/")
public class ScienceResource {

    private final Logger log = LoggerFactory.getLogger(ScienceResource.class);

    private final ScienceEventService scienceEventService;

    public ScienceResource(ScienceEventService scienceEventService) {
        this.scienceEventService = scienceEventService;
    }

    /**
     * PUT science : Logs an event of the given type in the event list
     *
     * @param type the type of the event that should be logged
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("science")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> science(@RequestBody String type) {
        log.debug("REST request to log science event of type {}", type);
        scienceEventService.logEvent(ScienceEventType.valueOf(type));
        return ResponseEntity.ok().build();
    }
}
