package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing TutorParticipation.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class TutorParticipationResource {

    private final Logger log = LoggerFactory.getLogger(TutorParticipationResource.class);

    private static final String ENTITY_NAME = "tutorParticipation";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TutorParticipationService tutorParticipationService;

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserService userService;

    public TutorParticipationResource(TutorParticipationService tutorParticipationService, AuthorizationCheckService authorizationCheckService, ExerciseService exerciseService,
            UserService userService) {
        this.tutorParticipationService = tutorParticipationService;
        this.exerciseService = exerciseService;
        this.authorizationCheckService = authorizationCheckService;
        this.userService = userService;
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations : start the "id" exercise for the current tutor. A tutor participation will be created and returned for the exercise given by
     * the exercise id. The tutor participation status will be assigned based on which features are available for the exercise (e.g. grading instructions) The method is valid only
     * for tutors, since it inits the tutor participation to the exercise, which is different from a standard participation
     *
     * @param exerciseId the id of the exercise for which to init a tutorParticipations
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     * @throws URISyntaxException if URI path can't be created
     */
    @PostMapping(value = "/exercises/{exerciseId}/tutorParticipations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TutorParticipation> initTutorParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start tutor participation : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }

        if (tutorParticipationService.existsByAssessedExerciseIdAndTutorId(exerciseId, user.getId())) {
            // tutorParticipation already exists
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "tutorParticipationAlreadyExists",
                    "There is already a tutorParticipations for the given exercise and user.")).body(null);
        }

        TutorParticipation tutorParticipation = tutorParticipationService.createNewParticipation(exercise, user);
        return ResponseEntity.created(new URI("/api/exercises/" + exerciseId + "tutorParticipations/" + tutorParticipation.getId())).body(tutorParticipation);
    }

    /**
     * POST /exercises/:exerciseId/exampleSubmission: Add an example submission to the tutor participation of the given exercise. If it is just for review (not used for tutorial),
     * the method just records that the tutor has read it. If it is a tutorial, the method checks if the assessment given by the tutor is close enough to the instructor one. If
     * yes, then it returns the participation, if not, it returns an error
     *
     * @param exerciseId the id of the exercise of the tutorParticipation
     * @param exampleSubmission the example submission that will be added
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/exampleSubmission")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<TutorParticipation> addExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to add example submission to exercise id : {}", exerciseId);
        Exercise exercise = this.exerciseService.findOne(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }

        TutorParticipation resultTutorParticipation = tutorParticipationService.addExampleSubmission(exercise, exampleSubmission, user);

        // Avoid infinite recursion for JSON
        resultTutorParticipation.getTrainedExampleSubmissions().forEach(trainedExampleSubmissioin -> {
            trainedExampleSubmissioin.setTutorParticipations(null);
            trainedExampleSubmissioin.setExercise(null);
        });

        return ResponseEntity.ok().body(resultTutorParticipation);
    }
}
