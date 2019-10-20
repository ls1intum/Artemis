package de.tum.in.www1.artemis.web.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Generic Resource for Text, Modeling and File Upload Submission REST Controllers
 */
public abstract class GenericSubmissionResource<T extends Submission, E extends Exercise> {

    @Value("${jhipster.clientApp.name}")
    protected String applicationName;

    private static final String ENTITY_NAME = "genericSubmission";

    protected final CourseService courseService;

    protected final AuthorizationCheckService authCheckService;

    protected final ExerciseService exerciseService;

    protected final UserService userService;

    protected final ParticipationService participationService;

    public GenericSubmissionResource(CourseService courseService, AuthorizationCheckService authCheckService, UserService userService, ExerciseService exerciseService,
            ParticipationService participationService) {
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
    }

    /**
     * Check if exercise is valid and the user can access it
     * @param exercise that we want to check
     * @return either null if exercise is valid or one of the error responses if it is not valid
     */
    ResponseEntity<T> checkExerciseValidity(E exercise) {
        if (exercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(exercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "courseNotFound", "The course belonging to this file upload exercise does not exist"))
                    .body(null);
        }
        if (!authCheckService.isAtLeastStudentInCourse(course, userService.getUserWithGroupsAndAuthorities())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }
}
