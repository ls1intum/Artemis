package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
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
import java.util.HashSet;
import java.util.List;

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
    private final ExampleSubmissionRepository exampleSubmissionRepository;

    public TutorParticipationResource(TutorParticipationService tutorParticipationService,
                                      CourseService courseService,
                                      ExerciseService exerciseService,
                                      UserService userService,
                                      ExampleSubmissionRepository exampleSubmissionRepository) {
        this.tutorParticipationService = tutorParticipationService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.userService = userService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations : start the "id" exercise for the current user.
     *
     * @param exerciseId the id of the exercise for which to init a tutorParticipations
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/tutorParticipations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
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
            // tutorParticipations already exists
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("tutorParticipations", "tutorParticipationAlreadyExists", "There is already a tutorParticipations for the given exercise and user.")).body(null);
        }
        TutorParticipation tutorParticipation = tutorParticipationService.createNewParticipation(exercise, user);
        return ResponseEntity.created(new URI("/api/exercises/" + exerciseId + "tutorParticipations/" + tutorParticipation.getId()))
            .body(tutorParticipation);
    }

    /**
     * POST /exercises/:exerciseId/tutorParticipations/:participationId/exampleSubmission: add an example submission to the tutor participation
     *
     * @param exerciseId      the id of the exercise of the tutorParticipations
     * @param participationId the tutor participation id
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/exercises/{exerciseId}/tutorParticipations/{participationId}/exampleSubmission")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<TutorParticipation> addExampleSubmission(@PathVariable Long exerciseId, @PathVariable Long participationId, @RequestBody ExampleSubmission exampleSubmission) throws URISyntaxException {
        log.debug("REST request to add example submission to participation id : {}", participationId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Course course = exercise.getCourse();

        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return forbidden();
        }

        TutorParticipation existingTutorParticipation = tutorParticipationService.findOne(participationId);

        if (existingTutorParticipation == null) {
            return ResponseEntity.notFound().build();
        }

        if (existingTutorParticipation.getStatus() != TutorParticipationStatus.REVIEWED_INSTRUCTIONS) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("tutorParticipations", "tutorParticipationInWrongStatus", "You cannot assess an example submission if you haven't read the grading instructions yet.")).body(null);
        }

        // TODO: check it is a valid example submission
        List<ExampleSubmission> alreadyAssessedSubmissions = this.exampleSubmissionRepository.findAllByExerciseIdAndTutorParticipation(exercise.getId(), existingTutorParticipation);
        alreadyAssessedSubmissions.add(exampleSubmission);
        existingTutorParticipation.setTrainedExampleSubmissions(new HashSet<>(alreadyAssessedSubmissions));

        int numberOfExampleSubmissions = this.exampleSubmissionRepository.findAllByExerciseId(exercise.getId()).size();

        /*
          When the tutor has assessed enough exercises (hardcoded to 3 at the moment), or when the tutor has assessed
          all exercises (maybe there are less example exercises than 3) the tutor status goes to the next step.
          TODO: make 3 a configuration option
         */
        if (alreadyAssessedSubmissions.size() == 3 || alreadyAssessedSubmissions.size() == numberOfExampleSubmissions) {
            existingTutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }

        tutorParticipationService.save(existingTutorParticipation);

        return ResponseEntity.ok().body(existingTutorParticipation);
    }
}
