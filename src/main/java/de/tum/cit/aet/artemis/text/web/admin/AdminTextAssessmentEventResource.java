package de.tum.cit.aet.artemis.text.web.admin;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.repository.TextAssessmentEventRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextAssessmentEvent;

/**
 * REST controller for administrating TextAssessmentEventResource.
 */
@Conditional(TextEnabled.class)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/text/admin/")
public class AdminTextAssessmentEventResource {

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    public AdminTextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    /**
     * Get admin/event-insights/text-assessment/events/{courseId} : Retrieve all the events from the 'text_assessment_event' table by course id
     *
     * @param courseIdQuery the id of the course to filter by (provided as a query parameter; preferred)
     * @param courseIdPath  the id of the course to filter by (provided as a legacy path variable; deprecated)
     * @return returns a List of TextAssessmentEvent's
     */
    @GetMapping({ "event-insights/text-assessment/events", "event-insights/text-assessment/events/{courseId}" })
    public ResponseEntity<List<TextAssessmentEvent>> getEventsByCourseId(@RequestParam(name = "courseId", required = false) Long courseIdQuery,
            @PathVariable(name = "courseId", required = false) Long courseIdPath) {
        Long courseId = courseIdQuery != null ? courseIdQuery : courseIdPath;
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(events);
    }
}
