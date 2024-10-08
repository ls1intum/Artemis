package de.tum.cit.aet.artemis.text.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.repository.TextAssessmentEventRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.text.domain.TextAssessmentEvent;

/**
 * REST controller for administrating TextAssessmentEventResource.
 */
@Profile(PROFILE_CORE)
@EnforceAdmin
@RestController
@RequestMapping("api/admin/")
public class AdminTextAssessmentEventResource {

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    public AdminTextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    /**
     * Get admin/event-insights/text-assessment/events/{courseId} : Retrieve all the events from the 'text_assessment_event' table by course id
     *
     * @param courseId the id of the course to filter by
     * @return returns a List of TextAssessmentEvent's
     */
    @GetMapping("event-insights/text-assessment/events/{courseId}")
    public ResponseEntity<List<TextAssessmentEvent>> getEventsByCourseId(@PathVariable Long courseId) {
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(events);
    }
}
