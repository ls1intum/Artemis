package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.repository.*;
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

    private final ExerciseRepository exerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    @Value("${info.text-assessment-analytics-enabled}")
    private Optional<Boolean> textAssessmentAnalyticsEnabled;

    public TextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            CourseRepository courseRepository, ExerciseRepository exerciseRepository, TextSubmissionRepository textSubmissionRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
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
     */
    @PostMapping("/events")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> addAssessmentEvent(@RequestBody TextAssessmentEvent event) {
        log.debug("REST request to save assessmentEvent : {}", event);

        // check if the text assessment analytics feature is enabled
        if (!isTextAssessmentAnalyticsEnabled()) {
            return forbidden();
        }

        // A new assessmentEvent cannot already have an ID
        if (event.getId() != null) {
            return ResponseEntity.badRequest().build();
        }

        // Save the event if it is valid. All other requests are considered bad requests.
        if (validateEvent(event)) {
            textAssessmentEventRepository.save(event);
            return ResponseEntity.ok().build();
        }
        else {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * This method checks that the event parameter is valid.
     * - The user id received should match the logged in users id.
     * - The user should be at least tutor of the course
     * - The course id received should exist
     * - The exercise id received should be an exercise of the course.
     * @param event the event to be validated
     * @return whether the event is valid or not
     */
    private boolean validateEvent(TextAssessmentEvent event) {
        // avoid access from tutor if they are not part of the course
        User user = userRepository.getUserWithGroupsAndAuthorities();
        // check that logged in user id and sent event user id match
        if (!user.getId().equals(event.getUserId())) {
            return false;
        }
        try {
            // check if user has enough roles to access the course
            Course course = courseRepository.findByIdElseThrow(event.getCourseId());
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                return false;
            }
            // Fetch the text submission by the received event submission id
            Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(event.getSubmissionId());
            log.debug("text submission {}", textSubmission.isPresent());
            log.debug("text submission {}", textSubmissionRepository.findAll());
            if (textSubmission.isEmpty()) {
                return false;
            }
            Long fetchedParticipationId = textSubmission.get().getParticipation().getId();
            Exercise fetchedExercise = textSubmission.get().getParticipation().getExercise();
            Long fetchedExerciseId = fetchedExercise.getId();
            Long fetchedCourseId = fetchedExercise.getCourseViaExerciseGroupOrCourseMember().getId();
            // check if the sent exercise id is valid
            return fetchedCourseId.equals(event.getCourseId()) && fetchedExerciseId.equals(event.getTextExerciseId()) && fetchedParticipationId.equals(event.getParticipationId());
        }
        catch (EntityNotFoundException exception) {
            // catch exception when event course id is malformed, or doesn't exist
            return false;
        }
    }
}
