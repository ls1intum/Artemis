package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;

/**
 * REST controller for administrating TextAssessmentEventResource.
 */
@RestController
@RequestMapping("api/admin/")
public class AdminTextAssessmentEventResource {

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    public AdminTextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    /**
     * Get /analytics/text-assessment/events/{courseId} : Retrieve all the events from the 'text_assessment_event' table by course id
     * @param courseId the id of the course to filter by
     * @return returns a List of TextAssessmentEvent's
     */
    @GetMapping("analytics/text-assessment/events/{courseId}")
    @EnforceAdmin
    public ResponseEntity<List<TextAssessmentEvent>> getEventsByCourseId(@PathVariable Long courseId) {
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(events);
    }
}
