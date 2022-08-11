package de.tum.in.www1.artemis.web.rest;

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
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing TextAssessmentEventResource.
 */
@RestController
@RequestMapping("/api/analytics/text-assessment")
public class TextAssessmentEventResource {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentEventResource.class);

    private final TextAssessmentEventRepository textAssessmentEventRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    @Value("${info.text-assessment-analytics-enabled}")
    private Optional<Boolean> textAssessmentAnalyticsEnabled;

    public TextAssessmentEventResource(TextAssessmentEventRepository textAssessmentEventRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            CourseRepository courseRepository, TextSubmissionRepository textSubmissionRepository) {
        this.textAssessmentEventRepository = textAssessmentEventRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
    }

    /**
     * The text assessment analytics are enabled when the configuration info.text-assessment-analytics-enabled is set to true.
     * A non-existing entry or false mean that the text assessment analytics is not enabled
     * @return whether the text assessment analytics are enabled or not
     */
    private boolean isTextAssessmentAnalyticsEnabled() {
        return textAssessmentAnalyticsEnabled.isPresent() && Boolean.TRUE.equals(textAssessmentAnalyticsEnabled.get());
    }

    /**
     * Get events/{courseId} : Retrieve all the events from the 'text_assessment_event' table by course id
     * @param courseId the id of the course to filter by
     * @return returns a List of TextAssessmentEvent's
     */
    @GetMapping("events/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TextAssessmentEvent>> getEventsByCourseId(@PathVariable Long courseId) {
        List<TextAssessmentEvent> events = textAssessmentEventRepository.findAllByCourseId(courseId);
        return ResponseEntity.ok().body(events);
    }

    /**
     * POST events : Adds an assessment event into the text_assessment_event table.
     * @param event to be added
     * @return the status of the finished request
     */
    @PostMapping("events")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> addAssessmentEvent(@RequestBody TextAssessmentEvent event) {
        log.debug("REST request to save assessmentEvent : {}", event);

        // Check if the text assessment analytics feature is enabled
        // Save the event if it is valid. All other requests are considered bad requests.
        if (isTextAssessmentAnalyticsEnabled() && validateEvent(event)) {
            textAssessmentEventRepository.save(event);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    /**
     * GET courses/{courseId}/text-exercises/{exerciseId}/tutors-involved : get the number of the tutors involved in the list of events
     * @param courseId the id of the course to query events for
     * @param exerciseId the id of the exercise to query events for
     * @return an integer representing the number of tutors involved for the respective course and exercise
     */
    @GetMapping("courses/{courseId}/text-exercises/{exerciseId}/tutors-involved")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Integer> getNumberOfTutorsInvolved(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        Integer numberOfTutors = textAssessmentEventRepository.getNumberOfTutorsInvolvedInAssessingByExerciseAndCourseId(courseId, exerciseId);
        log.debug("REST request to get number of tutors involved : {}", numberOfTutors);
        return ResponseEntity.ok().body(numberOfTutors);
    }

    /**
     * This method checks that the event parameter is valid.
     * - The user id received should match the logged-in users id.
     * - The user should be at least tutor of the course
     * - The course id received should exist
     * - The exercise id received should be an exercise of the course.
     * @param event the event to be validated
     * @return whether the event is valid or not
     */
    private boolean validateEvent(TextAssessmentEvent event) {
        // avoid access from tutor if they are not part of the course
        User user = userRepository.getUserWithGroupsAndAuthorities();

        // make sure that the received event doesn't already have an ID
        // reject if the logged-in user id and received event user id do not match
        // make sure that the event submission id is not null
        if (event.getId() != null || !user.getId().equals(event.getUserId()) || event.getSubmissionId() == null) {
            return false;
        }

        // check if user has enough roles to access the course
        if (!isUserInCourseWithId(user, event.getCourseId())) {
            return false;
        }

        // check if the received event submission data is valid
        return isEventSubmissionValid(event);
    }

    /**
     * Checks if the given user is at least a tutor in the course with the given id
     * @param user the user to be checked
     * @param courseId the id of the course that the user should be at least tutor of
     * @return whether the user is or isn't in the course specified
     */
    private boolean isUserInCourseWithId(User user, Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * Checks if the data corresponding to the submission in the given TextAssessmentEvent corresponds to an actual
     * submission that exists in the database. In case such a submission doesn't exist in the database it should be ignored.
     * @param event the event to be checked against the database
     * @return whether the event is valid or not
     */
    private boolean isEventSubmissionValid(TextAssessmentEvent event) {
        // Fetch the text submission by the received event submission id
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(event.getSubmissionId());
        if (textSubmission.isEmpty()) {
            return false;
        }
        // fetch all the relevant id's to be checked
        Long fetchedParticipationId = textSubmission.get().getParticipation().getId();
        Exercise fetchedExercise = textSubmission.get().getParticipation().getExercise();
        Long fetchedExerciseId = fetchedExercise.getId();
        Long fetchedCourseId = fetchedExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        // check if ids of the event ids match with the actual datas id in the repository.
        return fetchedCourseId.equals(event.getCourseId()) && fetchedExerciseId.equals(event.getTextExerciseId()) && fetchedParticipationId.equals(event.getParticipationId());
    }
}
