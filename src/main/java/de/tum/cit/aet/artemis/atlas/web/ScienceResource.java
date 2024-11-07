package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO;
import de.tum.cit.aet.artemis.atlas.service.ScienceEventService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

/**
 * REST controller providing the science related endpoints.
 */
@Profile(PROFILE_CORE)
@FeatureToggle(Feature.Science)
@RestController
@RequestMapping("api/")
public class ScienceResource {

    private static final Logger log = LoggerFactory.getLogger(ScienceResource.class);

    private final ScienceEventService scienceEventService;

    public ScienceResource(ScienceEventService scienceEventService) {
        this.scienceEventService = scienceEventService;
    }

    /**
     * PUT science : Logs an event of the given type in the event list
     *
     * @param event the type of the event that should be logged
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping(value = "science")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> science(@RequestBody ScienceEventDTO event) {
        log.debug("REST request to log science event of type {}", event);
        scienceEventService.logEvent(event);
        return ResponseEntity.ok().build();
    }
}
