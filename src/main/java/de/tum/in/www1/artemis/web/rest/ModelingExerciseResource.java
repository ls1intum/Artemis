package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ModelingExerciseService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

/**
 * REST controller for managing ModelingExercise.
 */
@RestController
@RequestMapping("/api")
public class ModelingExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ModelingExerciseResource.class);

    private static final String ENTITY_NAME = "modelingExercise";

    private final ModelingExerciseRepository modelingExerciseRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final AuthorizationCheckService authCheckService;
    private final ParticipationService participationService;
    private final ResultRepository resultRepository;
    private final ModelingExerciseService modelingExerciseService;
    private final CompassService compassService;

    public ModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository,
                                    UserService userService,
                                    AuthorizationCheckService authCheckService,
                                    CourseService courseService,
                                    ParticipationService participationService,
                                    ResultRepository resultRepository,
                                    ModelingExerciseService modelingExerciseService,
                                    CompassService compassService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseService = modelingExerciseService;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.compassService = compassService;
    }

    //TODO: most of these calls should be done in the context of a course

    /**
     * POST  /modeling-exercises : Create a new modelingExercise.
     *
     * @param modelingExercise the modelingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new modelingExercise, or with status 400 (Bad Request) if the modelingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/modeling-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingExercise> createModelingExercise(@RequestBody ModelingExercise modelingExercise) throws URISyntaxException {
        log.debug("REST request to save ModelingExercise : {}", modelingExercise);
        if (modelingExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new modelingExercise cannot already have an ID")).body(null);
        }
        ResponseEntity<ModelingExercise> responseFailure = checkModelingExercise(modelingExercise);
        if (responseFailure != null) return responseFailure;

        ModelingExercise result = modelingExerciseRepository.save(modelingExercise);
        return ResponseEntity.created(new URI("/api/modeling-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @Nullable
    private ResponseEntity<ModelingExercise> checkModelingExercise(@RequestBody ModelingExercise modelingExercise) {
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            return forbidden();
        }
        return null;
    }

    /**
     * PUT  /modeling-exercises : Updates an existing modelingExercise.
     *
     * @param modelingExercise the modelingExercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingExercise,
     * or with status 400 (Bad Request) if the modelingExercise is not valid,
     * or with status 500 (Internal Server Error) if the modelingExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/modeling-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingExercise> updateModelingExercise(@RequestBody ModelingExercise modelingExercise) throws URISyntaxException {
        log.debug("REST request to update ModelingExercise : {}", modelingExercise);
        if (modelingExercise.getId() == null) {
            return createModelingExercise(modelingExercise);
        }

        ResponseEntity<ModelingExercise> responseFailure = checkModelingExercise(modelingExercise);
        if (responseFailure != null) return responseFailure;

        ModelingExercise result = modelingExerciseRepository.save(modelingExercise);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, modelingExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /courses/:courseId/exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of modelingExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/modeling-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ModelingExercise>> getModelingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ModelingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        List<ModelingExercise> exercises = modelingExerciseRepository.findByCourseId(courseId);
        for (Exercise exercise : exercises) {
            //not required in the returned json body
            exercise.setParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET  /modeling-exercises/:id/statistics : get the "id" modelingExercise statistics.
     *
     * @param exerciseId the id of the modelingExercise for which the statistics should be retrieved
     * @return the json encoded modelingExercise statistics
     */
    @GetMapping(value = "/modeling-exercises/{exerciseId}/statistics")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getModelingExerciseStatistics(@PathVariable Long exerciseId) {
        log.debug("REST request to get ModelingExercise Statistics for Exercise: {}", exerciseId);
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findById(exerciseId);
        if (!modelingExercise.isPresent()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            return forbidden();
        }
        return ResponseEntity.ok(compassService.getStatistics(exerciseId).toString());
    }


    /**
     * GET  /modeling-exercises/:id : get the "id" modelingExercise.
     *
     * @param id the id of the modelingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingExercise, or with status 404 (Not Found)
     */
    @GetMapping("/modeling-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingExercise> getModelingExercise(@PathVariable Long id) {
        log.debug("REST request to get ModelingExercise : {}", id);
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findById(id);
        if (modelingExercise.isPresent()) {
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
                return forbidden();
            }
        }
        return ResponseUtil.wrapOrNotFound(modelingExercise);
    }

    /**
     * DELETE  /modeling-exercises/:id : delete the "id" modelingExercise.
     *
     * @param exerciseId the id of the modelingExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/modeling-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteModelingExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to delete ModelingExercise : {}", exerciseId);
        ModelingExercise modelingExercise = modelingExerciseRepository.findById(exerciseId).get();
        if (!authCheckService.isAtLeastInstructorForExercise(modelingExercise)) {
            return forbidden();
        }
        modelingExerciseService.delete(exerciseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, exerciseId.toString())).build();
    }

    /**
     * Returns the data needed for the modeling editor, which includes the participation, modelingSubmission with model if existing
     * and the assessments if the submission was already submitted.
     *
     * @param participationId the participationId for which to find the data for the modeling editor
     * @return the ResponseEntity with json as body
     */
    @GetMapping("/modeling-editor/{participationId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<ModelingSubmission> getDataForModelingEditor(@PathVariable Long participationId) {
        Participation participation = participationService.findOneWithEagerSubmissions(participationId);
        if (participation == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).body(null);
        }
        ModelingExercise modelingExercise;
        if (participation.getExercise() instanceof ModelingExercise) {
            modelingExercise = (ModelingExercise) participation.getExercise();
            if (modelingExercise == null) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("modelingExercise", "exerciseEmpty", "The exercise belonging to the participation is null.")).body(null);
            }

            //make sure the solution is not sent to the client
            modelingExercise.setSampleSolutionExplanation(null);
            modelingExercise.setSampleSolutionModel(null);

        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("modelingExercise", "wrongExerciseType", "The exercise of the participation is not a modeling exercise.")).body(null);
        }

        // users can only see their own models (to prevent cheating), TAs, instructors and admins can see all models
        if (authCheckService.isOwnerOfParticipation(participation) || courseService.userHasAtLeastTAPermissions(modelingExercise.getCourse())) {
            //continue
        } else {
            return forbidden();
        }

        // if no results, check if there are really no results or the relation to results was not updated yet
        if (participation.getResults().size() == 0) {
            List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
            participation.setResults(new HashSet<>(results));
        }

        Optional<ModelingSubmission> optionalModelingSubmission = participation.findLatestModelingSubmission();
        ModelingSubmission modelingSubmission;
        if (!optionalModelingSubmission.isPresent()) {
            modelingSubmission = new ModelingSubmission();  //NOTE: this object is not yet persisted
            modelingSubmission.setParticipation(participation);
        }
        else {
            //only try to get and set the model if the modelingSubmission existed before
            modelingSubmission = optionalModelingSubmission.get();
        }

        //make sure only the latest submission and latest result is sent to the client
        participation.setSubmissions(null);
        participation.setResults(null);

        if (modelingSubmission.getResult() instanceof HibernateProxy) {
            modelingSubmission.setResult((Result) Hibernate.unproxy(modelingSubmission.getResult()));
        }
        return ResponseEntity.ok(modelingSubmission);
    }
}
