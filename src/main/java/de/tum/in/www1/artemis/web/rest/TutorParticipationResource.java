package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.TutorParticipationService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing TutorParticipation.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class TutorParticipationResource {

    private final Logger log = LoggerFactory.getLogger(TutorParticipationResource.class);
    private final TutorParticipationService tutorParticipationService;
    private final ExerciseService exerciseService;
    private final CourseService courseService;
    private final UserService userService;

    public TutorParticipationResource(TutorParticipationService tutorParticipationService,
                                      CourseService courseService,
                                      ExerciseService exerciseService,
                                      UserService userService) {
        this.tutorParticipationService = tutorParticipationService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.userService = userService;
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations : start the "id" exercise for the current tutor.
     *
     * A tutor participation will be created and returned for the exercise given by the exercise id. The tutor
     * participation status will be assigned based on which features are available for the exercise (e.g. grading
     * instructions)
     *
     * The method is valid only for tutors, since it inits the tutor participation to the exercise, which is different
     * from a standard participation
     *
     * @param exerciseId the id of the exercise for which to init a tutorParticipations
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/tutorParticipations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TutorParticipation> initTutorParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start tutor participation : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Course course = exercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return forbidden();
        }

        TutorParticipation existingTutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (existingTutorParticipation != null && existingTutorParticipation.getId() != null) {
            // tutorParticipation already exists
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("tutorParticipations", "tutorParticipationAlreadyExists", "There is already a tutorParticipations for the given exercise and user.")).body(null);
        }

        TutorParticipation tutorParticipation = tutorParticipationService.createNewParticipation(exercise, user);
        return ResponseEntity.created(new URI("/api/exercises/" + exerciseId + "tutorParticipations/" + tutorParticipation.getId()))
            .body(tutorParticipation);
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations/:participationId/exampleSubmission: add an example submission to the tutor participation
     *
     * The tutor has read (if it is a tutorial) or assessed an example submission.
     * If it is a tutorial, the method just records that the tutor has read it.
     * If it is not, the method checks if the assessment given by the tutor is close enough to the instructor one.
     * If yes, then it returns the participation, if not, it returns an error
     *
     * @param exerciseId      the id of the exercise of the tutorParticipation
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/exampleSubmission")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TutorParticipation> addExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to add example submission to exercise id : {}", exerciseId);
        Exercise exercise = this.exerciseService.findOne(exerciseId);
        Course course = exercise.getCourse();

        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return forbidden();
        }

        TutorParticipation resultTutorParticipation = tutorParticipationService.addExampleSubmission(exercise, exampleSubmission);

        // Avoid infinite recursion for JSON
        resultTutorParticipation.getTrainedExampleSubmissions().forEach(t -> {
            t.setTutorParticipation(null);
            t.setExercise(null);
        });

        return ResponseEntity.ok().body(resultTutorParticipation);
    }
}
