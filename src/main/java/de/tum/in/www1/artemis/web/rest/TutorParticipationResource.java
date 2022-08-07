package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.GuidedTourConfiguration;
import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TutorParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.TutorParticipationService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing TutorParticipation.
 */
@RestController
@RequestMapping("/api")
public class TutorParticipationResource {

    private final Logger log = LoggerFactory.getLogger(TutorParticipationResource.class);

    private static final String ENTITY_NAME = "tutorParticipation";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TutorParticipationService tutorParticipationService;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final GuidedTourConfiguration guidedTourConfiguration;

    public TutorParticipationResource(TutorParticipationService tutorParticipationService, TutorParticipationRepository tutorParticipationRepository,
            AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository, UserRepository userRepository,
            GuidedTourConfiguration guidedTourConfiguration) {
        this.tutorParticipationService = tutorParticipationService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.guidedTourConfiguration = guidedTourConfiguration;
    }

    /**
     * POST /exercises/:exerciseId/tutor-participations : start the "id" exercise for the current tutor. A tutor participation will be created and returned for the exercise given by
     * the exercise id. The tutor participation status will be assigned based on which features are available for the exercise (e.g. grading instructions) The method is valid only
     * for tutors, since it inits the tutor participation to the exercise, which is different from a standard participation
     *
     * @param exerciseId the id of the exercise for which to init a tutorParticipations
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     * @throws URISyntaxException if URI path can't be created
     */
    @PostMapping(value = "/exercises/{exerciseId}/tutor-participations")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<TutorParticipation> initTutorParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start tutor participation : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);

        if (tutorParticipationRepository.existsByAssessedExerciseIdAndTutorId(exerciseId, user.getId())) {
            // tutorParticipation already exists
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "tutorParticipationAlreadyExists",
                    "There is already a tutorParticipations for the given exercise and user.")).body(null);
        }

        TutorParticipation tutorParticipation = tutorParticipationService.createNewParticipation(exercise, user);
        return ResponseEntity.created(new URI("/api/exercises/" + exerciseId + "tutor-participations/" + tutorParticipation.getId())).body(tutorParticipation);
    }

    /**
     * POST /exercises/:exerciseId/assess-example-submission: Add an example submission to the tutor participation of the given exercise.
     * If it is just for review (not used for tutorial), the method just records that the tutor has read it.
     * If it is a tutorial, the method checks if the assessment given by the tutor matches the instructor one.
     * If yes, then it returns the participation, if not, it returns an error.
     *
     * @param exerciseId the id of the exercise of the tutorParticipation
     * @param exampleSubmission the example submission that will be added
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/assess-example-submission")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<TutorParticipation> assessExampleSubmissionForTutorParticipation(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to add example submission to exercise id : {}", exerciseId);
        Exercise exercise = this.exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);

        TutorParticipation resultTutorParticipation = tutorParticipationService.addExampleSubmission(exercise, exampleSubmission, user);

        // Avoid infinite recursion for JSON
        resultTutorParticipation.getTrainedExampleSubmissions().forEach(trainedExampleSubmission -> {
            trainedExampleSubmission.setTutorParticipations(null);
            trainedExampleSubmission.setExercise(null);
        });

        return ResponseEntity.ok().body(resultTutorParticipation);
    }

    /**
     * DELETE /guided-tour/exercises/:exerciseId/example-submission: delete the tutor participation for example submissions of the "exerciseId" exercise for guided tutorials (e.g. when restarting a tutorial)
     * Please note: all tutors can delete their own tutor participation for example submissions when it belongs to a guided tutorial
     * @param exerciseId    the exercise id which has example submissions and tutor participations
     * @return  the ResponseEntity with status 200 (OK) or 403 (FORBIDDEN)
     */
    @DeleteMapping(value = "/guided-tour/exercises/{exerciseId}/example-submission")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<TutorParticipation> deleteTutorParticipationForGuidedTour(@PathVariable Long exerciseId) {
        log.debug("REST request to remove tutor participation of the example submission for exercise id : {}", exerciseId);
        Exercise exercise = this.exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        // Allow all tutors to delete their own participation if it's for a tutorial
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
        guidedTourConfiguration.checkExerciseForTutorialElseThrow(exercise);
        tutorParticipationService.removeTutorParticipations(exercise, user);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).build();
    }
}
