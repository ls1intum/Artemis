package de.tum.in.www1.artemis.web.rest;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

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
    private final ExerciseService exerciseService;
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
                                      ExerciseService exerciseService,
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
        this.exerciseService = exerciseService;
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
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }

        return handleModelingSubmission(exerciseId, principal, modelingSubmission);
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
    @Transactional
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(courseId, exerciseId, principal, modelingSubmission);
        }

        return handleModelingSubmission(exerciseId, principal, modelingSubmission);
    }

    @NotNull
    private ResponseEntity<ModelingSubmission> handleModelingSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        ResponseEntity<ModelingSubmission> responseFailure = checkExerciseValidity(modelingExercise);
        if (responseFailure != null) return responseFailure;

        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(modelingExercise.getId(), principal.getName());

        // update and save submission
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, participation);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }

    private void hideDetails(@RequestBody ModelingSubmission modelingSubmission) {
        //do not send old submissions or old results to the client
        if (modelingSubmission.getParticipation() != null) {
            modelingSubmission.getParticipation().setSubmissions(null);
            modelingSubmission.getParticipation().setResults(null);

            if (modelingSubmission.getParticipation().getExercise() != null && modelingSubmission.getParticipation().getExercise() instanceof ModelingExercise) {
                //make sure the solution is not sent to the client
                ModelingExercise modelingExerciseForClient = (ModelingExercise) modelingSubmission.getParticipation().getExercise();
                modelingExerciseForClient.setSampleSolutionExplanation(null);
                modelingExerciseForClient.setSampleSolutionModel(null);
            }
        }
    }

    @Nullable
    private ResponseEntity<ModelingSubmission> checkExerciseValidity(ModelingExercise modelingExercise) {
        if (modelingExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        Course course = modelingExercise.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return forbidden();
        }
        return null;
    }


    /**
     * GET  /modeling-submissions : get all the modelingSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of modelingSubmissions in body
     */
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ModelingSubmission>> getAllModelingSubmissions(@PathVariable Long courseId,
                                                                              @PathVariable Long exerciseId,
                                                                              @RequestParam(defaultValue = "false") boolean submittedOnly) {
        log.debug("REST request to get all ModelingSubmissions");
        Exercise exercise = exerciseService.findOneLoadParticipations(exerciseId);
        Course course = exercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return forbidden();
        }
        List<Participation> participations = participationService.findByExerciseIdWithEagerSubmissions(exerciseId);
        List<ModelingSubmission> submissions = new ArrayList<>();
        for (Participation participation : participations) {
            ModelingSubmission submission = participation.findLatestModelingSubmission();
            if (submission != null) {
                if (submittedOnly && !submission.isSubmitted()) {
                    //filter out non submitted submissions if the flag is set to true
                    continue;
                }
                submissions.add(submission);
            }
            //avoid infinite recursion
            participation.getExercise().setParticipations(null);
        }

        submissions.forEach(submission -> {
            Hibernate.initialize(submission.getResult()); // eagerly load the association
            if (submission.getResult() != null) {
                Hibernate.initialize(submission.getResult().getAssessor());
            }
        });

        return ResponseEntity.ok().body(submissions);
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
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithModel(@PathVariable Long courseId, @PathVariable Long exerciseId, @PathVariable Long id, Principal principal) {
        log.debug("REST request to get ModelingSubmission with model : {}", id);
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepository.findById(id);
        if (modelingSubmission.isPresent()) {
            Long studentId = userRepository.findUserIdBySubmissionId(id);
            JsonObject model = modelingSubmissionService.getModel(exerciseId, studentId, id);
            if (model != null) {
                modelingSubmission.get().setModel(model.toString());
            }
            hideDetails(modelingSubmission.get());
        }
        return ResponseUtil.wrapOrNotFound(modelingSubmission);
    }
}
