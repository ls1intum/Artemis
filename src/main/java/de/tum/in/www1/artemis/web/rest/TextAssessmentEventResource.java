package de.tum.in.www1.artemis.web.rest;

import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing TextAssessmentEventResource.
 */
@RestController
@RequestMapping("/api/text-assessment-event")
public class TextAssessmentEventResource {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentEventResource.class);

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public TextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            CourseRepository courseRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * This function retrieves all of the events from the 'text_assessment_event' table
     * @return returns a List of TextAssessmentEvent's
     */
    @GetMapping("/get-events")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<TextAssessmentEvent>> findAllEvents() {
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAll();
        return ResponseEntity.ok().body(events);
    }

    /**
     * This function adds an assessment event into the text_assessment_event table.
     * @param event to be added
     * @return the status of the finished request
     * @throws URISyntaxException
     */
    @PostMapping("/add-event")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> addAssessmentEvent(@RequestBody TextAssessmentEvent event) throws URISyntaxException {
        log.debug("REST request to save assessmentEvent : {}", event);

        // A new assessmentEvent cannot already have an ID
        if (event.getId() != null) {
            return ResponseEntity.badRequest().build();
        }

        // avoid access from tutor if they are not part of the course
        User user = userRepository.getUserWithGroupsAndAuthorities();
        try {
            Course course = courseRepository.findByIdElseThrow(event.getCourseId());
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
            textAssessmentEventRepository.save(event);
            return ResponseEntity.ok().build();
        }
        catch (EntityNotFoundException exception) {
            // catch exception when event course id is malformed, or doesn't exist
            return ResponseEntity.badRequest().build();
        }
    }
}
