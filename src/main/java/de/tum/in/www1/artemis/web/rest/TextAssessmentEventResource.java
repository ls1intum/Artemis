package de.tum.in.www1.artemis.web.rest;

import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing TextAssessmentEventResource.
 */
@RestController
@RequestMapping("/api/text-assessment-event")
public class TextAssessmentEventResource {

    private static final String ENTITY_NAME = "text_assessment_event";

    private final Logger log = LoggerFactory.getLogger(TextAssessmentEventResource.class);

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    public TextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    @GetMapping("/get-events")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.ARTEMIS_ANALYTICS)
    public ResponseEntity<List<TextAssessmentEvent>> findAllEvents() {
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAll();
        return ResponseEntity.ok().body(events);
    }

    @PostMapping("/add-event")
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.ARTEMIS_ANALYTICS)
    public ResponseEntity<Void> addAssessmentEvent(@RequestBody TextAssessmentEvent event) throws URISyntaxException {
        log.debug("REST request to save assessmentEvent : {}", event);
        if (event.getId() != null) {
            throw new BadRequestAlertException("A new assessmentEvent cannot already have an ID", ENTITY_NAME, "idexists");
        }

        // basic check if tutor in current course

        textAssessmentEventRepository.save(event);
        return ResponseEntity.ok().build();
    }
}
