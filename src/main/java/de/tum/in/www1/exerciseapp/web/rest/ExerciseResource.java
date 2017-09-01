package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.ProgrammingExercise;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
import de.tum.in.www1.exerciseapp.service.ContinuousIntegrationService;
import de.tum.in.www1.exerciseapp.service.ExerciseService;
import de.tum.in.www1.exerciseapp.service.VersionControlService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import de.tum.in.www1.exerciseapp.web.rest.util.PaginationUtil;
import io.github.jhipster.web.util.ResponseUtil;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing Exercise.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class ExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseResource.class);

    private static final String ENTITY_NAME = "exercise";

    private final ExerciseRepository exerciseRepository;
    private final ExerciseService exerciseService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<VersionControlService> versionControlService;

    public ExerciseResource(ExerciseRepository exerciseRepository, ExerciseService exerciseService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseService = exerciseService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
    }

    /**
     * POST  /exercises : Create a new exercise.
     *
     * @param exercise the exercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new exercise, or with status 400 (Bad Request) if the exercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/exercises")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    //TODO: test if it still works with abstract entity in body
    public ResponseEntity<Exercise> createExercise(@RequestBody Exercise exercise) throws URISyntaxException {
        log.debug("REST request to save Exercise : {}", exercise);
        if (exercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new exercise cannot already have an ID")).body(null);
        }
        if(exercise instanceof ProgrammingExercise) {
            ResponseEntity<Exercise> errorResponse = checkProgrammingExerciseForError((ProgrammingExercise) exercise);
            if(errorResponse != null) {
                return errorResponse;
            }
        }
        Exercise result = exerciseRepository.save(exercise);
        return ResponseEntity.created(new URI("/api/exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     *
     * @param exercise
     * @return the error message as response or null if everything is fine
     */
    private ResponseEntity<Exercise> checkProgrammingExerciseForError(ProgrammingExercise exercise) {
        if(!continuousIntegrationService.get().buildPlanIdIsValid(exercise.getBaseBuildPlanId())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("exercise", "invalid.build.plan.id", "The Base Build Plan ID seems to be invalid.")).body(null);
        }
        if(!versionControlService.get().repositoryUrlIsValid(exercise.getBaseRepositoryUrlAsUrl())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("exercise", "invalid.repository.url", "The Repository URL seems to be invalid.")).body(null);
        }
        return null;
    }

    /**
     * PUT  /exercises : Updates an existing exercise.
     *
     * @param exercise the exercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated exercise,
     * or with status 400 (Bad Request) if the exercise is not valid,
     * or with status 500 (Internal Server Error) if the exercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/exercises")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    //TODO: test if it still works with abstract entity in body
    public ResponseEntity<Exercise> updateExercise(@RequestBody Exercise exercise) throws URISyntaxException {
        log.debug("REST request to update Exercise : {}", exercise);
        if (exercise.getId() == null) {
            return createExercise(exercise);
        }
        if(exercise instanceof ProgrammingExercise) {
            ResponseEntity<Exercise> errorResponse = checkProgrammingExerciseForError((ProgrammingExercise) exercise);
            if(errorResponse != null) {
                return errorResponse;
            }
        }
        Exercise result = exerciseRepository.save(exercise);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, exercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /exercises : get all the exercises.
     *
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of exercises in body
     */
    @GetMapping("/exercises")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<List<Exercise>> getAllExercises(@ApiParam Pageable pageable) {
        log.debug("REST request to get a page of Exercises");
        Page<Exercise> page = exerciseRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/exercises");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET  /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId the course for which to retrieve all exercises
     * @param pageable the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of exercises in body
     * @throws URISyntaxException if there is an error to generate the pagination HTTP headers
     */
    @GetMapping(value = "/courses/{courseId}/exercises")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    @Transactional(readOnly = true)
    public ResponseEntity<List<Exercise>> getExercisesForCourse(@PathVariable Long courseId, @RequestParam(defaultValue = "false") boolean withLtiOutcomeUrlExisting, @PageableDefault(value = 100)  Pageable pageable, Principal principal)
        throws URISyntaxException {
        log.debug("REST request to get a page of Exercises");
        Page<Exercise> page = withLtiOutcomeUrlExisting ? exerciseRepository.findByCourseIdWhereLtiOutcomeUrlExists(courseId, principal, pageable) : exerciseRepository.findByCourseId(courseId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/courses/" + courseId + "exercises");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * GET  /exercises/:id : get the "id" exercise.
     *
     * @param id the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<Exercise> getExercise(@PathVariable Long id) {
        log.debug("REST request to get Exercise : {}", id);
        Exercise exercise = exerciseRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(exercise));
    }

    /**
     * DELETE  /exercises/:id : delete the "id" exercise.
     *
     * @param id the id of the exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/exercises/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<Void> deleteExercise(@PathVariable Long id,
                                               @RequestParam(defaultValue = "false") boolean deleteParticipations) {
        log.debug("REST request to delete Exercise : {}", id);
        exerciseService.delete(id, deleteParticipations);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }

    /**
     * DELETE  /exercises/:id/participations : delete all participations of "id" exercise (reset).
     *
     * @param id the id of the exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping(value = "/exercises/{id}/participations")
    @PreAuthorize("hasAnyRole('ADMIN', 'TA')")
    @Timed
    public ResponseEntity<Void> resetExercise(@PathVariable Long id) {
        log.debug("REST request to reset Exercise : {}", id);
        Exercise exercise = exerciseRepository.findOne(id);
        exerciseService.reset(exercise);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert("exercise", id.toString())).build();
    }
}
