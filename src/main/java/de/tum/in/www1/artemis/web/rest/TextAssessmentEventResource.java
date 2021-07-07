package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.TextAssesmentEvent;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing TextAssessmentEventResource.
 */
@RestController
@RequestMapping("/api/text-assessment-event")
// @PreAuthorize("hasRole('ADMIN')")
public class TextAssessmentEventResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "text_assessment_event";

    private final Logger log = LoggerFactory.getLogger(TextAssessmentEventResource.class);

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    public TextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    @GetMapping("/get-events")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TextAssesmentEvent>> findAllEvents() {
        List<TextAssesmentEvent> events = textAssessmentEventRepository.findAll();
        return ResponseEntity.ok().body(events);
    }

    @PostMapping("/add-event")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<TextAssesmentEvent> addAssessmentEvent(@RequestBody TextAssesmentEvent event) throws URISyntaxException {
        log.debug("REST request to save assessmentEvent : {}", event);
        if (event.getId() != null) {
            throw new BadRequestAlertException("A new assessmentEvent cannot already have an ID", ENTITY_NAME, "idexists");
        }

        // basic check if tutor in current course

        TextAssesmentEvent savedEvent = textAssessmentEventRepository.save(event);
        return ResponseEntity.created(new URI("/api/text-assessment-event/add-event/" + savedEvent.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedEvent.getEventType().toString())).body(savedEvent);
    }
}
