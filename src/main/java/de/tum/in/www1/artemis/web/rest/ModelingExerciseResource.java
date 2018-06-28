package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
import de.tum.in.www1.artemis.repository.JsonAssessmentRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ModelingSubmissionService modelingSubmissionService;
    private final ResultRepository resultRepository;
    private final ObjectMapper objectMapper;
    private final JsonAssessmentRepository jsonAssessmentRepository;
    private final ModelingExerciseService modelingExerciseService;

    public ModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository,
                                    UserService userService,
                                    AuthorizationCheckService authCheckService,
                                    CourseService courseService,
                                    ParticipationService participationService,
                                    ModelingSubmissionService modelingSubmissionService,
                                    ResultRepository resultRepository,
                                    MappingJackson2HttpMessageConverter springMvcJacksonConverter,
                                    JsonAssessmentRepository jsonAssessmentRepository,
                                    ModelingExerciseService modelingExerciseService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseService = modelingExerciseService;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.resultRepository = resultRepository;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.jsonAssessmentRepository = jsonAssessmentRepository;
    }

    /**
     * POST  /modeling-exercises : Create a new modelingExercise.
     *
     * @param modelingExercise the modelingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new modelingExercise, or with status 400 (Bad Request) if the modelingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/modeling-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ModelingExercise> createModelingExercise(@RequestBody ModelingExercise modelingExercise) throws URISyntaxException {
        log.debug("REST request to save ModelingExercise : {}", modelingExercise);
        if (modelingExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new modelingExercise cannot already have an ID")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ModelingExercise result = modelingExerciseRepository.save(modelingExercise);
        return ResponseEntity.created(new URI("/api/modeling-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
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
    @Timed
    public ResponseEntity<ModelingExercise> updateModelingExercise(@RequestBody ModelingExercise modelingExercise) throws URISyntaxException {
        log.debug("REST request to update ModelingExercise : {}", modelingExercise);
        if (modelingExercise.getId() == null) {
            return createModelingExercise(modelingExercise);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(modelingExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this modeling exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ModelingExercise result = modelingExerciseRepository.save(modelingExercise);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, modelingExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /modeling-exercises : get all the modelingExercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of modelingExercises in body
     */
    @GetMapping("/modeling-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public List<ModelingExercise> getAllModelingExercises() {
        log.debug("REST request to get all ModelingExercises");
        List<ModelingExercise> exercises = modelingExerciseRepository.findAll();
        User user = userService.getUserWithGroupsAndAuthorities();
        Stream<ModelingExercise> authorizedExercises = exercises.stream().filter(
            exercise -> {
                Course course = exercise.getCourse();
                return authCheckService.isTeachingAssistantInCourse(course, user) ||
                    authCheckService.isInstructorInCourse(course, user) ||
                    authCheckService.isAdmin();
            }
        );
        return authorizedExercises.collect(Collectors.toList());
    }

    /**
     * GET  /courses/:courseId/exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of modelingExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/modeling-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    @Transactional(readOnly = true)
    public ResponseEntity<List<ModelingExercise>> getModelingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ModelingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<ModelingExercise> exercises = modelingExerciseRepository.findByCourseId(courseId);

        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET  /modeling-exercises/:id : get the "id" modelingExercise.
     *
     * @param id the id of the modelingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingExercise, or with status 404 (Not Found)
     */
    @GetMapping("/modeling-exercises/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<ModelingExercise> getModelingExercise(@PathVariable Long id) {
        log.debug("REST request to get ModelingExercise : {}", id);
        ModelingExercise modelingExercise = modelingExerciseRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(modelingExercise));
    }

    /**
     * DELETE  /modeling-exercises/:id : delete the "id" modelingExercise.
     *
     * @param id the id of the modelingExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/modeling-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteModelingExercise(@PathVariable Long id) {
        log.debug("REST request to delete ModelingExercise : {}", id);
        ModelingExercise modelingExercise = modelingExerciseRepository.findOne(id);
        Course course = modelingExercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        modelingExerciseService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }

    @GetMapping("/modeling-editor/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    @Timed
    public ResponseEntity<JsonNode> getDataForModelingEditor(@PathVariable Long id) {
        Participation participation = participationService.findOne(id);
        if (participation == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).body(null);
        }
        ModelingExercise modelingExercise = (ModelingExercise) participation.getExercise();

        if (!courseService.userHasStudentPermissions(modelingExercise.getCourse())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // if no results, check if there are really no results or the relation to results was not updated yet
        if (participation.getResults().size() <= 0) {
            List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
            participation.setResults(new HashSet<>(results));
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.set("participation", objectMapper.valueToTree(participation));
        if (participation.getResults().size() > 0) {
            Result result = participation.getResults().iterator().next();
            ModelingSubmission modelingSubmission;
            if (result.getSubmission() instanceof HibernateProxy) {
                modelingSubmission = (ModelingSubmission) Hibernate.unproxy(result.getSubmission());
            } else if (result.getSubmission() instanceof ModelingSubmission) {
                modelingSubmission = (ModelingSubmission) result.getSubmission();
            } else {
                modelingSubmission = modelingSubmissionService.findByParticipation(participation);
            }
            JsonObject model = modelingSubmissionService.getModel(participation.getExercise().getId(), participation.getStudent().getId(), modelingSubmission.getId());
            if (model != null) {
                modelingSubmission.setModel(model.toString());
            }
            data.set("modelingSubmission", objectMapper.valueToTree(modelingSubmission));
            if (modelingSubmission.isSubmitted() && result.isRated()) {
                if (jsonAssessmentRepository.exists(modelingExercise.getId(), participation.getStudent().getId(), modelingSubmission.getId(), true)) {
                    JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(modelingExercise.getId(), participation.getStudent().getId(), modelingSubmission.getId(), true);
                    try {
                        data.set("assessments", objectMapper.readTree(assessmentJson.get("assessments").toString()));
                    } catch (IOException e) {

                    }
                } else if (jsonAssessmentRepository.exists(modelingExercise.getId(), participation.getStudent().getId(), modelingSubmission.getId(), false)) {
                    JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(modelingExercise.getId(), participation.getStudent().getId(), modelingSubmission.getId(), false);
                    try {
                        data.set("assessments", objectMapper.readTree(assessmentJson.get("assessments").toString()));
                    } catch (IOException e) {

                    }
                }
            }
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/assessment-editor/{exerciseId}/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    @Timed
    public ResponseEntity<JsonNode> getDataForAssessmentEditor(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        ModelingExercise modelingExercise = modelingExerciseRepository.findOne(exerciseId);

        Course course = modelingExercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Result> result = resultRepository.findDistinctBySubmissionId(submissionId);
        if (result.isPresent()) {
            Result relevantResult = result.get();
            ModelingSubmission modelingSubmission;
            if (relevantResult.getSubmission() instanceof HibernateProxy) {
                modelingSubmission = (ModelingSubmission) Hibernate.unproxy(relevantResult.getSubmission());
            } else if (relevantResult.getSubmission() instanceof ModelingSubmission) {
                modelingSubmission = (ModelingSubmission) relevantResult.getSubmission();
            } else {
                modelingSubmission = modelingSubmissionService.findByParticipation(relevantResult.getParticipation());
            }
            JsonObject model = modelingSubmissionService.getModel(modelingExercise.getId(), relevantResult.getParticipation().getStudent().getId(), submissionId);
            if (model != null) {
                modelingSubmission.setModel(model.toString());
            }
            ObjectNode data = objectMapper.createObjectNode();
            data.set("modelingExercise", objectMapper.valueToTree(modelingExercise));
            data.set("result", objectMapper.valueToTree(relevantResult));
            data.set("modelingSubmission", objectMapper.valueToTree(modelingSubmission));
            if (modelingSubmission.isSubmitted()) {
                if (jsonAssessmentRepository.exists(modelingExercise.getId(), relevantResult.getParticipation().getStudent().getId(), modelingSubmission.getId(), true)) {
                    JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(modelingExercise.getId(), relevantResult.getParticipation().getStudent().getId(), modelingSubmission.getId(), true);
                    try {
                        data.set("assessments", objectMapper.readTree(assessmentJson.get("assessments").toString()));
                    } catch (IOException e) {

                    }
                } else if (jsonAssessmentRepository.exists(modelingExercise.getId(), relevantResult.getParticipation().getStudent().getId(), modelingSubmission.getId(), false)) {
                    JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(modelingExercise.getId(), relevantResult.getParticipation().getStudent().getId(), modelingSubmission.getId(), false);
                    try {
                        data.set("assessments", objectMapper.readTree(assessmentJson.get("assessments").toString()));
                    } catch (IOException e) {

                    }
                }
            }
            return ResponseEntity.ok(data);
        }
        return ResponseEntity.ok(null);
    }
}
