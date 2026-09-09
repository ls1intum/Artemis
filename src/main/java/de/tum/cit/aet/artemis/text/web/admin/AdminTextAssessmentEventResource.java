package de.tum.cit.aet.artemis.text.web.admin;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.repository.TextAssessmentEventRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextAssessmentEvent;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentEventDTO;

/**
 * REST controller for administrating TextAssessmentEventResource.
 */
@Conditional(TextEnabled.class)
@EnforceAdmin
@Lazy
@FeatureUsage("assessment/assessment-analytics")
@RestController
@RequestMapping("api/text/admin/")
public class AdminTextAssessmentEventResource {

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    public AdminTextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
    }

    /**
     * Get admin/event-insights/text-assessment/events : Retrieve all the events from the 'text_assessment_event' table by course id
     *
     * @param courseId the id of the course to filter by
     * @return returns a List of TextAssessmentEventDTO's
     */
    @GetMapping("event-insights/text-assessment/events")
    public ResponseEntity<List<TextAssessmentEventDTO>> getEventsByCourseId(@RequestParam long courseId) {
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(events.stream().map(TextAssessmentEventDTO::of).toList());
    }
}
