package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Course;
import de.tum.in.www1.exerciseapp.domain.ProgrammingExercise;
import de.tum.in.www1.exerciseapp.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.exerciseapp.service.*;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * REST controller for managing ProgrammingExercise.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private final ProgrammingExerciseRepository programmingExerciseRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final AuthorizationCheckService authCheckService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<VersionControlService> versionControlService;

    public ProgrammingExerciseResource(ProgrammingExerciseRepository programmingExerciseRepository, UserService userService,
                                       AuthorizationCheckService authCheckService, CourseService courseService,
                                       Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
    }

    /**
     *
     * @param exercise the exercise object we want to check for errors
     * @return the error message as response or null if everything is fine
     */
    private ResponseEntity<ProgrammingExercise> checkProgrammingExerciseForError(ProgrammingExercise exercise) {
        if(!continuousIntegrationService.get().buildPlanIdIsValid(exercise.getBaseBuildPlanId())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("exercise", "invalid.build.plan.id", "The Base Build Plan ID seems to be invalid.")).body(null);
        }
        if(!versionControlService.get().repositoryUrlIsValid(exercise.getBaseRepositoryUrlAsUrl())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("exercise", "invalid.repository.url", "The Repository URL seems to be invalid.")).body(null);
        }
        return null;
    }

    /**
     * POST  /programming-exercises : Create a new programmingExercise.
     *
     * @param programmingExercise the programmingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the programmingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/programming-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ProgrammingExercise> createProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) throws URISyntaxException {
        log.debug("REST request to save ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new programmingExercise cannot already have an ID")).body(null);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this programming exercise does not exist")).body(null);
        }
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
            !authCheckService.isInstructorInCourse(course) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ResponseEntity<ProgrammingExercise> errorResponse = checkProgrammingExerciseForError(programmingExercise);
        if(errorResponse != null) {
            return errorResponse;
        }
        ProgrammingExercise result = programmingExerciseRepository.save(programmingExercise);
        return ResponseEntity.created(new URI("/api/programming-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /programming-exercises : Updates an existing programmingExercise.
     *
     * @param programmingExercise the programmingExercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated programmingExercise,
     * or with status 400 (Bad Request) if the programmingExercise is not valid,
     * or with status 500 (Internal Server Error) if the programmingExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/programming-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ProgrammingExercise> updateProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) throws URISyntaxException {
        log.debug("REST request to update ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() == null) {
            return createProgrammingExercise(programmingExercise);
        }
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(programmingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this programming exercise does not exist")).body(null);
        }
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
            !authCheckService.isInstructorInCourse(course) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ResponseEntity<ProgrammingExercise> errorResponse = checkProgrammingExerciseForError(programmingExercise);
        if(errorResponse != null) {
            return errorResponse;
        }
        ProgrammingExercise result = programmingExerciseRepository.save(programmingExercise);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, programmingExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /programming-exercises : get all the programmingExercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping("/programming-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public List<ProgrammingExercise> getAllProgrammingExercises() {
        log.debug("REST request to get all ProgrammingExercises");
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAll();
        Stream<ProgrammingExercise> authorizedExercises = exercises.stream().filter(
            exercise -> {
                Course course = exercise.getCourse();
                return authCheckService.isTeachingAssistantInCourse(course) ||
                    authCheckService.isInstructorInCourse(course) ||
                    authCheckService.isAdmin();
            }
        );
        return authorizedExercises.collect(Collectors.toList());
    }

    /**
     * GET  /courses/:courseId/exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/programming-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProgrammingExercise>> getProgrammingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
             !authCheckService.isInstructorInCourse(course) &&
             !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByCourseId(courseId);

        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET  /programming-exercises/:id : get the "id" programmingExercise.
     *
     * @param id the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping("/programming-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable Long id) {
        log.debug("REST request to get ProgrammingExercise : {}", id);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findOne(id);
        Course course = programmingExercise.getCourse();
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
             !authCheckService.isInstructorInCourse(course) &&
             !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(programmingExercise));
    }

    /**
     * DELETE  /programming-exercises/:id : delete the "id" programmingExercise.
     *
     * @param id the id of the programmingExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/programming-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteProgrammingExercise(@PathVariable Long id) {
        log.debug("REST request to delete ProgrammingExercise : {}", id);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findOne(id);
        Course course = programmingExercise.getCourse();
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
             !authCheckService.isInstructorInCourse(course) &&
             !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        programmingExerciseRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
