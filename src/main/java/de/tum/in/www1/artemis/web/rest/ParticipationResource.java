package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * REST controller for managing Participation.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class ParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);
    private final ParticipationService participationService;
    private final QuizExerciseService quizExerciseService;
    private final ExerciseService exerciseService;
    private final CourseService courseService;
    private final AuthorizationCheckService authCheckService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<VersionControlService> versionControlService;

    private static final String ENTITY_NAME = "participation";

    public ParticipationResource(ParticipationService participationService,
                                 CourseService courseService,
                                 QuizExerciseService quizExerciseService,
                                 ExerciseService exerciseService,
                                 AuthorizationCheckService authCheckService,
                                 Optional<ContinuousIntegrationService> continuousIntegrationService,
                                 Optional<VersionControlService> versionControlService) {
        this.participationService = participationService;
        this.quizExerciseService = quizExerciseService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
    }

    /**
     * POST  /participations : Create a new participation.
     *
     * @param participation the participation to create
     * @return the ResponseEntity with status 201 (Created) and with body the new participation, or with status 400 (Bad Request) if the participation has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/participations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> createParticipation(@RequestBody Participation participation) throws URISyntaxException {
        log.debug("REST request to save Participation : {}", participation);
        Course course = participation.getExercise().getCourse();
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (participation.getId() != null) {
            throw new BadRequestAlertException("A new participation cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Participation result = participationService.save(participation);
        return ResponseEntity.created(new URI("/api/participations/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/participations : start the "id" exercise for the current user.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to init a participation
     * @param principal  the current user principal
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participations")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> initParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) throws URISyntaxException {
        log.debug("REST request to start Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Course course = exercise.getCourse();
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (Optional.ofNullable(exercise).isPresent()) {
            if (participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName()) != null) {
                // participation already exists
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("participation", "participationAlreadyExists", "There is already a participation for the given exercise and user.")).body(null);
            }
            Participation participation = participationService.startExercise(exercise, principal.getName());
            return ResponseEntity.created(new URI("/api/participations/" + participation.getId()))
                .body(participation);
        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("participation", "exerciseNotFound", "No exercise was found for the given ID")).body(null);
        }
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/resume-participation: resume the participation of the current user in the exercise identified by id
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId id of the exercise for which to resume participation
     * @param principal  current user principal
     * @return ResponseEntity with status 200 (OK) and with updated participation as a body, or with status 500 (Internal Server Error)
     */
    @PutMapping(value = "/courses/{courseId}/exercises/{exerciseId}/resume-participation")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> resumeParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) throws URISyntaxException {
        log.debug("REST request to resume Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndStudentLogin(exerciseId, principal.getName());
        Course course = participation.getExercise().getCourse();
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            if(!courseService.userHasAtLeastTAPermissions(course)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        if (exercise instanceof ProgrammingExercise) {
            participation = participationService.resume(exercise, participation);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, participation.getId().toString()))
                .body(participation);
        }
        log.debug("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to resume participation", exerciseId);
        return ResponseEntity.ok().body(participation);
    }

    /**
     * PUT  /participations : Updates an existing participation.
     *
     * @param participation the participation to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated participation,
     * or with status 400 (Bad Request) if the participation is not valid,
     * or with status 500 (Internal Server Error) if the participation couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/participations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> updateParticipation(@RequestBody Participation participation) throws URISyntaxException {
        log.debug("REST request to update Participation : {}", participation);
        Course course = participation.getExercise().getCourse();
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (participation.getId() == null) {
            return createParticipation(participation);
        }
        Participation result = participationService.save(participation);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, participation.getId().toString()))
            .body(result);
    }

    /**
     * GET  /exercise/:exerciseId/participations : get all the participations for an exercise
     *
     * @param exerciseId
     * @return
     */
    @GetMapping(value = "/exercise/{exerciseId}/participations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<List<Participation>> getAllParticipationsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get all Participations for Exercise {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Course course = exercise.getCourse();
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Participation> participations = participationService.findByExerciseId(exerciseId);
        return ResponseEntity.ok(participations);
    }

    /**
     * GET  /courses/:courseId/participations : get all the participations for a course
     *
     * @param courseId
     * @return
     */
    @GetMapping(value = "/courses/{courseId}/participations")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<List<Participation>> getAllParticipationsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Participations for Course {}", courseId);
        Course course = courseService.findOne(courseId);
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<Participation> participations = participationService.findByCourseId(courseId);
        return ResponseEntity.ok().body(participations);
    }

    /**
     * GET  /participations/:id : get the "id" participation.
     *
     * @param id the id of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("/participations/{id}/withLatestResult")
    @Timed
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> getParticipation(@PathVariable Long id) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOneWithEagerResults(id);
        if (participation == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Result result = participation.getExercise().findLatestRelevantResult(participation);
        Set<Result> results = new HashSet<Result>();
        if (result != null) {
            results.add(result);
        }
        participation.setResults(results);
        Course course = participation.getExercise().getCourse();
        if (!authCheckService.isOwnerOfParticipation(participation)) {
             if(!courseService.userHasAtLeastTAPermissions(course)) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
             }
        }
        return  new ResponseEntity<>(participation, HttpStatus.OK);
    }

    /**
     * GET  /participations/:id : get the "id" participation.
     *
     * @param id the id of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("/participations/{id}")
    @Timed
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> getParticipationWithLatestResult(@PathVariable Long id) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);
        Course course = participation.getExercise().getCourse();
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            if(!courseService.userHasAtLeastTAPermissions(course)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        return Optional.ofNullable(participation)
            .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/participations/{id}/repositoryWebUrl")
    @Timed
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getParticipationRepositoryWebUrl(@PathVariable Long id, Authentication authentication) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);
        Course course = participation.getExercise().getCourse();
        if (!authCheckService.isOwnerOfParticipation(participation)) {
             if(!courseService.userHasAtLeastTAPermissions(course)) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
             }
        }
        URL url = versionControlService.get().getRepositoryWebUrl(participation);
        return Optional.ofNullable(url)
            .map(result -> new ResponseEntity<>(
                url.toString(),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/participations/{id}/buildPlanWebUrl")
    @Timed
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getParticipationBuildPlanWebUrl(@PathVariable Long id) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);
        if (participation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Course course = participation.getExercise().getCourse();
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            if(!courseService.userHasAtLeastTAPermissions(course)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else if (!courseService.userHasAtLeastTAPermissions(course)) {
            // Check if build plan URL is published, if user is owner of participation and is not TA or higher
            if (participation.getExercise() instanceof ProgrammingExercise) {
                ProgrammingExercise programmingExercise = (ProgrammingExercise) participation.getExercise();
                if (!programmingExercise.isPublishBuildPlanUrl()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        }

        URL url = continuousIntegrationService.get().getBuildPlanWebUrl(participation);
        return Optional.ofNullable(url)
            .map(result -> new ResponseEntity<>(
                url.toString(),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    @GetMapping(value = "/participations/{id}/buildArtifact")
    @Timed
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity getParticipationBuildArtifact(@PathVariable Long id) {
        log.debug("REST request to get Participation build artifact: {}", id);
        Participation participation = participationService.findOne(id);
        Course course = participation.getExercise().getCourse();
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            if(!courseService.userHasAtLeastTAPermissions(course)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        return continuousIntegrationService.get().retrieveLatestArtifact(participation);
    }


    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participation: get the user's participation for a specific exercise.
     *
     * Please note: 'courseId' is only included in the call for API consistency, it is not actually used
     * //TODO remove courseId from the URL
     * @param exerciseId the id of the exercise for which to retrieve the participation
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participation")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    @Transactional(readOnly = true)
    public ResponseEntity<MappingJacksonValue> getParticipation(@PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request to get Participation for Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (exercise == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Course course = exercise.getCourse();
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MappingJacksonValue response;
        if (exercise instanceof QuizExercise) {
            response = participationForQuizExercise((QuizExercise) exercise, principal.getName());
        } else if (exercise instanceof ModelingExercise) {
            Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName());
            if (participation != null) {
                participation.getResults().size(); // eagerly load the association
            }
            response = participation == null ? null : new MappingJacksonValue(participation);
        } else {
            Participation participation = participationService.findOneByExerciseIdAndStudentLogin(exerciseId, principal.getName());
            response = participation == null ? null : new MappingJacksonValue(participation);
        }
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    private MappingJacksonValue participationForQuizExercise(QuizExercise quizExercise, String username) {
        if (!quizExercise.isStarted()) {
            // Quiz hasn't started yet => no Result, only quizExercise without questions
            quizExercise.filterSensitiveInformation();
            Participation participation = new Participation().exercise(quizExercise);
            return new MappingJacksonValue(participation);
        } else if (quizExercise.isSubmissionAllowed()) {
            // Quiz is active => construct Participation from
            // filtered quizExercise and submission from HashMap
            quizExercise = quizExerciseService.findOneWithQuestions(quizExercise.getId());
            quizExercise.filterForStudentsDuringQuiz();
            Participation participation = participationService.getParticipationForQuiz(quizExercise, username);
            // set view
            Class view = quizExerciseService.viewForStudentsInQuizExercise(quizExercise);
            MappingJacksonValue value = new MappingJacksonValue(participation);
            value.setSerializationView(view);
            return value;
        } else {
            // quiz has ended => get participation from database and add full quizExercise
            quizExercise = quizExerciseService.findOneWithQuestions(quizExercise.getId());
            Participation participation = participationService.getParticipationForQuiz(quizExercise, username);
            //avoid problems due to bidirectional associations between submission and result during serialization
            for (Result result : participation.getResults()) {
                if (result.getSubmission() != null) {
                    result.getSubmission().setResult(null);
                    result.getSubmission().setParticipation(null);
                }
            }
            return new MappingJacksonValue(participation);
        }
    }

    /**
     * GET  /participations/:id/status: get build status of the user's participation for the "id" participation.
     *
     * @param id the participation id
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping(value = "/participations/{id}/status")
    @Timed
    public ResponseEntity<?> getParticipationStatus(@PathVariable Long id) {
        Participation participation = participationService.findOne(id);
        // NOTE: Disable Authorization check for increased performance
        // (Unauthorized users being unable to see any participation's status is not a priority!)
        if (participation.getExercise() instanceof QuizExercise) {
            QuizExercise.Status status = QuizExercise.statusForQuiz((QuizExercise) participation.getExercise());
            return new ResponseEntity<>(status, HttpStatus.OK);
        } else if (participation.getExercise() instanceof ProgrammingExercise) {
            ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.get().getBuildStatus(participation);
            return Optional.ofNullable(buildStatus)
                .map(status -> new ResponseEntity<>(
                    status,
                    HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
        }
        return ResponseEntity.unprocessableEntity().build();
    }

    /**
     * DELETE  /participations/:id : delete the "id" participation.
     *
     * @param id the id of the participation to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/participations/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteParticipation(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "false") boolean deleteBuildPlan,
                                                    @RequestParam(defaultValue = "false") boolean deleteRepository) {
        log.debug("REST request to delete Participation : {}, deleteBuildPlan: {}, deleteRepository: {}", id, deleteBuildPlan, deleteRepository);
        Participation participation = participationService.findOne(id);
        Course course = participation.getExercise().getCourse();
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        participationService.delete(id, deleteBuildPlan, deleteRepository);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("participation", id.toString())).build();
    }
}
