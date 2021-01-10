package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.plagiarism.ModelingPlagiarismDetectionService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
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

    private final ModelingExerciseImportService modelingExerciseImportService;

    private final SubmissionExportService modelingSubmissionExportService;

    private final GroupNotificationService groupNotificationService;

    private final CompassService compassService;

    private final GradingCriterionService gradingCriterionService;

    private final ModelingPlagiarismDetectionService modelingPlagiarismDetectionService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    public ModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository, UserService userService, AuthorizationCheckService authCheckService,
            CourseService courseService, ModelingExerciseService modelingExerciseService, ModelingExerciseImportService modelingExerciseImportService,
            SubmissionExportService modelingSubmissionExportService, GroupNotificationService groupNotificationService, CompassService compassService,
            ExerciseService exerciseService, GradingCriterionService gradingCriterionService, ModelingPlagiarismDetectionService modelingPlagiarismDetectionService,
            ExampleSubmissionRepository exampleSubmissionRepository) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.modelingExerciseService = modelingExerciseService;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.modelingSubmissionExportService = modelingSubmissionExportService;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.compassService = compassService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
        this.gradingCriterionService = gradingCriterionService;
        this.modelingPlagiarismDetectionService = modelingPlagiarismDetectionService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
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

    /**
     * This method performs the basic checks for a modeling exercise. These include making sure that a course
     * or exerciseGroup has been set, otherwise we return http badRequest.
     * If the above condition is met, it then checks whether the user is an instructor of the parent course.
     * If not, it returns http forbidden.
     * @param modelingExercise The modeling exercise to be checked
     * @return Returns the http error, or null if successful
     */
    @Nullable
    private ResponseEntity<ModelingExercise> checkModelingExercise(@RequestBody ModelingExercise modelingExercise) {
        if (modelingExercise.getCourseViaExerciseGroupOrCourseMember() != null) {
            // fetch course from database to make sure client didn't change groups
            Course course = courseService.findOne(modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
            if (authCheckService.isAtLeastInstructorInCourse(course, null)) {
                if (modelingExercise.hasCourse() && modelingExercise.hasExerciseGroup()) {
                    return badRequest();
                }
                return null;
            }
            return forbidden();
        }
        return badRequest();
    }

    /**
     * Search for all modeling exercises by title and course title. The result is pageable since there might be hundreds
     * of exercises in the DB.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("/modeling-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR, ADMIN')")
    public ResponseEntity<SearchResultPageDTO<ModelingExercise>> getAllExercisesOnPage(PageableSearchDTO<String> search) {
        final var user = userService.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(modelingExerciseService.getAllOnPageWithSize(search, user));
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

        ModelingExercise updatedModelingExercise = modelingExerciseRepository.save(modelingExercise);

        // Avoid recursions
        if (updatedModelingExercise.getExampleSubmissions().size() != 0) {
            Set<ExampleSubmission> exampleSubmissionsWithResults = exampleSubmissionRepository.findAllWithEagerResultByExerciseId(updatedModelingExercise.getId());
            updatedModelingExercise.setExampleSubmissions(exampleSubmissionsWithResults);
            updatedModelingExercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setExercise(null));
            updatedModelingExercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setTutorParticipations(null));
        }

        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(modelingExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, modelingExercise.getId().toString()))
                .body(updatedModelingExercise);
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
        if (compassService.isSupported(modelingExercise.get())) {
            return ResponseEntity.ok(compassService.getStatistics(exerciseId).toString());
        }
        else {
            return notFound();
        }
    }

    /**
     * Prints the Compass statistic regarding the automatic assessment of the modeling exercise with the given id.
     *
     * @param exerciseId the id of the modeling exercise for which we want to get the Compass statistic
     * @return the statistic as key-value pairs in json
     */
    @GetMapping("/modeling-exercises/{exerciseId}/print-statistic")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> printCompassStatisticForExercise(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        compassService.printStatistic(modelingExercise.getId());
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
        Optional<ModelingExercise> modelingExercise = modelingExerciseRepository.findWithEagerExampleSubmissionsById(exerciseId);
        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        modelingExercise.ifPresent(exercise -> exercise.setGradingCriteria(gradingCriteria));
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
        Course course = modelingExercise.get().getCourseViaExerciseGroupOrCourseMember();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(modelingExercise.get(), course, user);
        exerciseService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, modelingExercise.get().getTitle())).build();
    }

    /**
     * POST /modeling-exercises/import: Imports an existing modeling exercise into an existing course
     *
     * This will import the whole exercise except for the participations and Dates.
     * Referenced entities will get cloned and assigned a new id.
     * Uses {@link ModelingExerciseImportService}.
     * See {@link ExerciseImportService#importExercise(Exercise, Exercise)}
     *
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param importedExercise The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @throws URISyntaxException When the URI of the response entity is invalid
     *
     * @return The imported exercise (200), a not found error (404) if the template does not exist, or a forbidden error
     *         (403) if the user is not at least an instructor in the target course.
     */
    @PostMapping("/modeling-exercises/import/{sourceExerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingExercise> importExercise(@PathVariable long sourceExerciseId, @RequestBody ModelingExercise importedExercise) throws URISyntaxException {
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            return badRequest();
        }
        final var user = userService.getUserWithGroupsAndAuthorities();
        final var optionalOriginalModelingExercise = modelingExerciseRepository.findByIdWithEagerExampleSubmissionsAndResults(sourceExerciseId);
        if (optionalOriginalModelingExercise.isEmpty()) {
            log.debug("Cannot find original exercise to import from {}", sourceExerciseId);
            return notFound();
        }

        ResponseEntity<ModelingExercise> responseFailure = checkModelingExercise(importedExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        if (importedExercise.hasExerciseGroup()) {
            log.debug("REST request to import text exercise {} into exercise group {}", sourceExerciseId, importedExercise.getExerciseGroup().getId());
        }
        else {
            log.debug("REST request to import text exercise with {} into course {}", sourceExerciseId, importedExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        }

        final var originalModelingExercise = optionalOriginalModelingExercise.get();

        if (!authCheckService.isAtLeastInstructorInCourse(originalModelingExercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
            log.debug("User {} is not allowed to import exercises from course {}", user.getId(), originalModelingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
            return forbidden();
        }

        final var newExercise = modelingExerciseImportService.importExercise(originalModelingExercise, importedExercise);
        if (newExercise == null) {
            return conflict();
        }

        modelingExerciseRepository.save((ModelingExercise) newExercise);
        return ResponseEntity.created(new URI("/api/modeling-exercises/" + newExercise.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newExercise.getId().toString())).body((ModelingExercise) newExercise);
    }

    /**
     * POST /modeling-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("/modeling-exercises/{exerciseId}/export-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {

        Optional<ModelingExercise> optionalModelingExercise = modelingExerciseRepository.findById(exerciseId);
        if (optionalModelingExercise.isEmpty()) {
            return notFound();
        }
        ModelingExercise modelingExercise = optionalModelingExercise.get();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            return forbidden();
        }

        // ta's are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants() && !authCheckService.isAtLeastInstructorInCourse(modelingExercise.getCourseViaExerciseGroupOrCourseMember(), null)) {
            return forbidden();
        }

        try {
            Optional<File> zipFile = modelingSubmissionExportService.exportStudentSubmissions(exerciseId, submissionExportOptions);

            if (zipFile.isEmpty()) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "nosubmissions", "No existing user was specified or no submission exists."))
                        .body(null);
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile.get()));
            return ResponseEntity.ok().contentLength(zipFile.get().length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.get().getName())
                    .body(resource);

        }
        catch (IOException e) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }
    }

    /**
     * GET /check-plagiarism : Run similarity check pair-wise against all submissions of a given
     * exercises. This can be used with human intelligence to identify suspicious similar
     * submissions which might be a sign for plagiarism.
     *
     * @param exerciseId for which all submission should be checked
     * @return the ResponseEntity with status 200 (OK) and the list of pair-wise submission
     * similarities above a threshold of 80%.
     */
    @GetMapping("/modeling-exercises/{exerciseId}/check-plagiarism")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingPlagiarismResult> checkPlagiarism(@PathVariable long exerciseId, @RequestParam float similarityThreshold, @RequestParam int minimumScore,
            @RequestParam int minimumSize) {
        Optional<ModelingExercise> optionalModelingExercise = modelingExerciseService.findOneWithParticipationsSubmissionsResults(exerciseId);

        if (optionalModelingExercise.isEmpty()) {
            return notFound();
        }

        ModelingExercise modelingExercise = optionalModelingExercise.get();

        if (!authCheckService.isAtLeastInstructorForExercise(modelingExercise)) {
            return forbidden();
        }

        ModelingPlagiarismResult result = modelingPlagiarismDetectionService.compareSubmissions(modelingExercise, similarityThreshold / 100, minimumSize, minimumScore);

        return ResponseEntity.ok(result);
    }
}
