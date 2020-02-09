package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/** REST controller for managing ModelingExercise. */
@RestController
@RequestMapping("/api")
public class ModelingExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ModelingExerciseResource.class);

    private static final String ENTITY_NAME = "modelingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final UserService userService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ModelingExerciseService modelingExerciseService;

    private final ExerciseService exerciseService;

    private final GroupNotificationService groupNotificationService;

    private final CompassService compassService;

    public ModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository, UserService userService, AuthorizationCheckService authCheckService,
            CourseService courseService, ModelingExerciseService modelingExerciseService, GroupNotificationService groupNotificationService, CompassService compassService,
            ExerciseService exerciseService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseService = modelingExerciseService;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.compassService = compassService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
    }

    // TODO: most of these calls should be done in the context of a course

    /**
     * POST /modeling-exercises : Create a new modelingExercise.
     *
     * @param modelingExercise the modelingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new modelingExercise, or with status 400 (Bad Request) if the modelingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/modeling-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingExercise> createModelingExercise(@RequestBody ModelingExercise modelingExercise) throws URISyntaxException {
        log.debug("REST request to save ModelingExercise : {}", modelingExercise);
        if (modelingExercise.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idexists", "A new modeling exercise cannot already have an ID")).body(null);
        }
        ResponseEntity<ModelingExercise> responseFailure = checkModelingExercise(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        ModelingExercise result = modelingExerciseRepository.save(modelingExercise);
        groupNotificationService.notifyTutorGroupAboutExerciseCreated(modelingExercise);
        return ResponseEntity.created(new URI("/api/modeling-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    @Nullable
    private ResponseEntity<ModelingExercise> checkModelingExercise(@RequestBody ModelingExercise modelingExercise) {
        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        return null;
    }

    /**
     * PUT /modeling-exercises : Updates an existing modelingExercise.
     *
     * @param modelingExercise the modelingExercise to update
     * @param notificationText the text shown to students
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingExercise, or with status 400 (Bad Request) if the modelingExercise is not valid, or with
     *         status 500 (Internal Server Error) if the modelingExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/modeling-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingExercise> updateModelingExercise(@RequestBody ModelingExercise modelingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update ModelingExercise : {}", modelingExercise);
        if (modelingExercise.getId() == null) {
            return createModelingExercise(modelingExercise);
        }

        ResponseEntity<ModelingExercise> responseFailure = checkModelingExercise(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        // As persisting is cascaded for example submissions we have to set the reference to the exercise in the
        // example submissions. Otherwise the connection between exercise and example submissions would be lost.
        if (modelingExercise.getExampleSubmissions() != null) {
            modelingExercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setExercise(modelingExercise));
        }

        ModelingExercise result = modelingExerciseRepository.save(modelingExercise);
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(modelingExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, modelingExercise.getId().toString())).body(result);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of modelingExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/modeling-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelingExercise>> getModelingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ModelingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        List<ModelingExercise> exercises = modelingExerciseRepository.findByCourseId(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /modeling-exercises/:id/statistics : get the "id" modelingExercise statistics.
     *
     * @param exerciseId the id of the modelingExercise for which the statistics should be retrieved
     * @return the json encoded modelingExercise statistics
     */
    @GetMapping(value = "/modeling-exercises/{exerciseId}/statistics")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getModelingExerciseStatistics(@PathVariable Long exerciseId) {
        log.debug("REST request to get ModelingExercise Statistics for Exercise: {}", exerciseId);
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findById(exerciseId);
        if (modelingExercise.isEmpty()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            return forbidden();
        }
        if (compassService.isSupported(modelingExercise.get().getDiagramType())) {
            return ResponseEntity.ok(compassService.getStatistics(exerciseId).toString());
        }
        else {
            return notFound();
        }
    }

    /**
     * Return the Compass statistic regarding the automatic assessment of the modeling exercise with the given id.
     *
     * @param exerciseId the id of the modeling exercise for which we want to get the Compass statistic
     * @return the statistic as key-value pairs in json
     */
    @GetMapping("/exercises/{exerciseId}/compass-statistic")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> getCompassStatisticForExercise(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        compassService.printStatistic(modelingExercise.getId());

        // TODO: In the future, this endpoint should return the statistics to the client as well.

        return ResponseEntity.ok().build();
    }

    /**
     * GET /modeling-exercises/:id : get the "id" modelingExercise.
     *
     * @param exerciseId the id of the modelingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingExercise, or with status 404 (Not Found)
     */
    @GetMapping("/modeling-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingExercise> getModelingExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ModelingExercise : {}", exerciseId);
        // TODO CZ: provide separate endpoint GET /modeling-exercises/{id}/withExampleSubmissions and load exercise without example submissions here
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findByIdWithEagerExampleSubmissions(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            return forbidden();
        }
        return ResponseUtil.wrapOrNotFound(modelingExercise);
    }

    /**
     * DELETE /modeling-exercises/:id : delete the "id" modelingExercise.
     *
     * @param exerciseId the id of the modelingExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/modeling-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteModelingExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete ModelingExercise : {}", exerciseId);
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findById(exerciseId);
        if (modelingExercise.isEmpty()) {
            return notFound();
        }
        Course course = modelingExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(modelingExercise.get(), course, user);
        exerciseService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, modelingExercise.get().getTitle())).build();
    }
}
