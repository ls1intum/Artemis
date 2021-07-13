package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing TextAssessmentEventResource.
 */
@RestController
@RequestMapping("/analytics/text-assessment")
public class TextAssessmentEventResource {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentEventResource.class);

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    @Value("${info.text-assessment-analytics-enabled}")
    private Optional<Boolean> textAssessmentAnalyticsEnabled;

    public TextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            CourseRepository courseRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * The text assessment analytics are enabled when the configuration info.text-assessment-analytics-enabled is set to true.
     * A non existing entry or false mean that the text assessment analytics is not enabled
     * @return whether the text assessment analytics are enabled or not
     */
    private boolean isTextAssessmentAnalyticsEnabled() {
        return textAssessmentAnalyticsEnabled.isPresent() && Boolean.TRUE.equals(textAssessmentAnalyticsEnabled.get());
    }

    /**
     * This function retrieves all of the events from the 'text_assessment_event' table by course id
     * @param courseId the id of the course to filter by
     * @return returns a List of TextAssessmentEvent's
     */
    @GetMapping("/events/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TextAssessmentEvent>> getEventsByCourseId(@PathVariable Long courseId) {
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(events);
    }

    /**
     * This function adds an assessment event into the text_assessment_event table.
     * @param event to be added
     * @return the status of the finished request
     * @throws URISyntaxException
     */
    @PostMapping("/events")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> addAssessmentEvent(@RequestBody TextAssessmentEvent event) throws URISyntaxException {
        log.debug("REST request to save assessmentEvent : {}", event);

        if (!isTextAssessmentAnalyticsEnabled()) {
            return forbidden();
        }

        // A new assessmentEvent cannot already have an ID
        if (event.getId() != null) {
            return ResponseEntity.badRequest().build();
        }

        // avoid access from tutor if they are not part of the course
        User user = userRepository.getUserWithGroupsAndAuthorities();
        try {
            Course course = courseRepository.findByIdElseThrow(event.getCourseId());
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                return forbidden();
            }
            textAssessmentEventRepository.save(event);
            return ResponseEntity.ok().build();
        }
        catch (EntityNotFoundException exception) {
            // catch exception when event course id is malformed, or doesn't exist
            return ResponseEntity.badRequest().build();
        }
    }
}
