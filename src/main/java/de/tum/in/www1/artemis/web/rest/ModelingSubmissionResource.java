package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing ModelingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ModelingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";

    private final JsonAssessmentRepository jsonAssessmentRepository;
    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final ModelingSubmissionService modelingSubmissionService;
    private final ModelingExerciseService modelingExerciseService;
    private final ParticipationService participationService;
    private final ResultRepository resultRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final AuthorizationCheckService authCheckService;

    public ModelingSubmissionResource(JsonAssessmentRepository jsonAssessmentRepository,
                                      ModelingSubmissionRepository modelingSubmissionRepository,
                                      ModelingSubmissionService modelingSubmissionService,
                                      ModelingExerciseService modelingExerciseService,
                                      ParticipationService participationService,
                                      ResultRepository resultRepository,
                                      UserRepository userRepository,
                                      UserService userService,
                                      CourseService courseService,
                                      AuthorizationCheckService authCheckService) {
        this.jsonAssessmentRepository = jsonAssessmentRepository;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseService = modelingExerciseService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
    }

    /**
     * POST  /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Create a new modelingSubmission.
     * This is called when a student saves his model the first time after starting the exercise or starting a retry.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param modelingSubmission the modelingSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @Timed
    public ResponseEntity<Result> createModelingSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to save ModelingSubmission : {}", modelingSubmission);
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }

        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        if (modelingExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Participation participation = participationService.init(modelingExercise, principal.getName());

        modelingSubmission.setType(SubmissionType.MANUAL);
        modelingSubmission.setSubmitted(false);

        // update and save submission
        try {
            // TODO DB logic update: remove generating result because we do not need the result as a bridge between participation and submission anymore
            Result result = modelingSubmissionService.save(modelingSubmission, modelingExercise, participation);

            participation.addResult(result);
            participation.addSubmissions(modelingSubmission);
            participationService.save(participation);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // return 409 conflict error
            // if this occurs, then it means that the createModelingSubmission function was called multiple times for the same participation id
            return ResponseEntity.status(409).build();
        }
    }

    /**
     * PUT  /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Updates an existing modelingSubmission.
     * This function is called by the modeling editor for saving and submitting modeling submissions.
     * The submit specific handling occurs in the ModelingSubmissionService.save() function.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param modelingSubmission the modelingSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingSubmission,
     * or with status 400 (Bad Request) if the modelingSubmission is not valid,
     * or with status 500 (Internal Server Error) if the modelingSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses/{courseId}/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> updateModelingSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) throws URISyntaxException {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission);
        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(courseId, exerciseId, principal, modelingSubmission);
        }

        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        if (modelingExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("modelingExercise", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName());

        // TODO DB logic update: remove generating result for save actions because we do not need the result as a bridge between participation and submission anymore
        Result result = modelingSubmissionService.save(modelingSubmission, modelingExercise, participation);
        if (modelingSubmission != result.getSubmission()) {
            modelingSubmission = (ModelingSubmission) result.getSubmission();
        }

        if (modelingSubmission.isSubmitted()) {
            participation.setInitializationState(ParticipationState.FINISHED);
            participationService.save(participation);
        }

        if (jsonAssessmentRepository.exists(exerciseId, user.getId(), modelingSubmission.getId(), false)) {
            result = resultRepository.findOne(result.getId());
            result.setSubmission(modelingSubmission);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * GET  /modeling-submissions : get all the modelingSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of modelingSubmissions in body
     */
    @GetMapping("/modeling-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public List<ModelingSubmission> getAllModelingSubmissions() {
        log.debug("REST request to get all ModelingSubmissions");
        return modelingSubmissionRepository.findAll();
    }

    /**
     * GET  /modeling-submissions/:id : get the "id" modelingSubmission.
     *
     * @param id the id of the modelingSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/modeling-submissions/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long id) {
        log.debug("REST request to get ModelingSubmission : {}", id);
        ModelingSubmission modelingSubmission = modelingSubmissionRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(modelingSubmission));
    }

    /**
     * DELETE  /modeling-submissions/:id : delete the "id" modelingSubmission.
     *
     * @param id the id of the modelingSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/modeling-submissions/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteModelingSubmission(@PathVariable Long id) {
        log.debug("REST request to delete ModelingSubmission : {}", id);
        modelingSubmissionRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }

    /**
     * GET  /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Gets an existing modelingSubmission.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param id             the id of the modelingSubmission to retrieve
     * @param principal      the current user principal
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingSubmission,
     * or with status 400 (Bad Request) if the modelingSubmission is not valid,
     * or with status 500 (Internal Server Error) if the modelingSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @GetMapping("/courses/{courseId}/exercises/{exerciseId}/modeling-submissions/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithModel(@PathVariable Long courseId, @PathVariable Long exerciseId, @PathVariable Long id, Principal principal) {
        log.debug("REST request to get ModelingSubmission with model : {}", id);
        ModelingSubmission modelingSubmission = modelingSubmissionRepository.findOne(id);
        if (Optional.ofNullable(modelingSubmission).isPresent()) {
            Long studentId = userRepository.findUserIdBySubmissionId(id);
            JsonObject model = modelingSubmissionService.getModel(exerciseId, studentId, id);
            if (model != null) {
                modelingSubmission.setModel(model.toString());
            }
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(modelingSubmission));
    }

    /**
     * GET  /modeling-submissions/:participationId : get the modelingSubmission for the participationId.
     *
     * @param participationId the id of the participation for which modelingSubmission is retrieved
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/modeling-submissions/participation/{participationId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ModelingSubmission> getModelingSubmissionByParticipation(@PathVariable Long participationId) {
        Participation participation = participationService.findOne(participationId);
        ModelingSubmission modelingSubmission = modelingSubmissionService.findLatestModelingSubmissionByParticipation(participation);
        if (Optional.ofNullable(modelingSubmission).isPresent()) {
            JsonObject model = modelingSubmissionService.getModel(participation.getExercise().getId(), participation.getStudent().getId(), modelingSubmission.getId());
            if (model != null) {
                modelingSubmission.setModel(model.toString());
            }
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(modelingSubmission));
    }
}
