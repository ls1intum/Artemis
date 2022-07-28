package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.FILE_ENDING_PATTERN;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;

/** REST controller for managing FileUploadExercise. */
@RestController
@RequestMapping(FileUploadExerciseResource.Endpoints.ROOT)
public class FileUploadExerciseResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseResource.class);

    private static final String ENTITY_NAME = "fileUploadExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final CourseRepository courseRepository;

    private final ParticipationRepository participationRepository;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final FileUploadSubmissionExportService fileUploadSubmissionExportService;

    private final InstanceMessageSendService instanceMessageSendService;

    public FileUploadExerciseResource(FileUploadExerciseRepository fileUploadExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, GroupNotificationService groupNotificationService, ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService,
            FileUploadSubmissionExportService fileUploadSubmissionExportService, GradingCriterionRepository gradingCriterionRepository, CourseRepository courseRepository,
            ParticipationRepository participationRepository, InstanceMessageSendService instanceMessageSendService) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.fileUploadSubmissionExportService = fileUploadSubmissionExportService;
        this.courseRepository = courseRepository;
        this.participationRepository = participationRepository;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * POST /file-upload-exercises : Create a new fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/file-upload-exercises")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<FileUploadExercise> createFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to save FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "A new fileUploadExercise cannot already have an ID", "idexists")).body(null);
        }
        // validates general settings: points, dates
        fileUploadExercise.validateGeneralSettings();
        // Validate the new file upload exercise
        validateNewOrUpdatedFileUploadExercise(fileUploadExercise);
        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);
        // Check that the user is authorized to create the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);
        groupNotificationService.checkNotificationsForNewExercise(fileUploadExercise, instanceMessageSendService);

        return ResponseEntity.created(new URI("/api/file-upload-exercises/" + result.getId())).body(result);
    }

    private boolean isFilePatternValid(FileUploadExercise exercise) {
        // a file ending should consist of a comma separated list of 1-5 characters / digits
        // when an empty string "" is passed in the exercise the file-pattern is null when it arrives in the rest endpoint
        if (exercise.getFilePattern() == null) {
            return false;
        }
        var filePattern = exercise.getFilePattern().toLowerCase().replaceAll("\\s+", "");
        var allowedFileEndings = filePattern.split(",");
        var isValid = true;
        for (var allowedFileEnding : allowedFileEndings) {
            isValid = isValid && FILE_ENDING_PATTERN.matcher(allowedFileEnding).matches();
        }

        if (isValid) {
            // use the lowercase version without whitespaces
            exercise.setFilePattern(filePattern);
            return true;
        }
        return false;
    }

    private void validateNewOrUpdatedFileUploadExercise(FileUploadExercise fileUploadExercise) throws BadRequestAlertException {
        // Valid exercises have set either a course or an exerciseGroup
        fileUploadExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        if (!isFilePatternValid(fileUploadExercise)) {
            throw new BadRequestAlertException("The file pattern is invalid. Please use a comma separated list with actual file endings without dots (e.g. 'png, pdf').",
                    ENTITY_NAME, "filepattern.invalid");
        }
    }

    /**
     * PUT /file-upload-exercises : Updates an existing fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to update
     * @param notificationText the text shown to students
     * @param exerciseId the id of exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise is not valid, or
     *         with status 500 (Internal Server Error) if the fileUploadExercise couldn't be updated
     */
    @PutMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<FileUploadExercise> updateFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText, @PathVariable Long exerciseId) {
        log.debug("REST request to update FileUploadExercise : {}", fileUploadExercise);
        // TODO: The route has an exerciseId but we don't do anything useful with it. Change route and client requests?

        // Validate the updated file upload exercise
        validateNewOrUpdatedFileUploadExercise(fileUploadExercise);
        // validates general settings: points, dates
        fileUploadExercise.validateGeneralSettings();

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);
        final var fileUploadExerciseBeforeUpdate = fileUploadExerciseRepository.findByIdElseThrow(fileUploadExercise.getId());

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(fileUploadExercise, fileUploadExerciseBeforeUpdate, ENTITY_NAME);

        var updatedExercise = fileUploadExerciseRepository.save(fileUploadExercise);
        exerciseService.logUpdate(updatedExercise, updatedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(fileUploadExerciseBeforeUpdate, updatedExercise);

        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedExercise, fileUploadExerciseBeforeUpdate.getDueDate());

        groupNotificationService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(fileUploadExerciseBeforeUpdate, updatedExercise, notificationText,
                instanceMessageSendService);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).body(updatedExercise);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of fileUploadExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/file-upload-exercises")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<FileUploadExercise>> getFileUploadExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<FileUploadExercise> exercises = fileUploadExerciseRepository.findByCourseIdWithCategories(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /file-upload-exercises/:exerciseId : get the "id" fileUploadExercise.
     *
     * @param exerciseId the id of the fileUploadExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadExercise, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long exerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get FileUploadExercise : {}", exerciseId);
        var exercise = fileUploadExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("FileUploadExercise", exerciseId));
        // If the exercise belongs to an exam, only editors or above are allowed to access it, otherwise also TA have access
        if (exercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        }
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);
        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, exercise);
        return ResponseEntity.ok().body(exercise);
    }

    /**
     * DELETE /file-upload-exercises/:exerciseId : delete the "id" fileUploadExercise.
     *
     * @param exerciseId the id of the fileUploadExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteFileUploadExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete FileUploadExercise : {}", exerciseId);
        var exercise = fileUploadExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(exercise, exercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exercise.getTitle())).build();
    }

    /**
     * POST /file-upload-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("/file-upload-exercises/{exerciseId}/export-submissions")
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {
        var exercise = fileUploadExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        // TAs are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants()) {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, exercise.getCourseViaExerciseGroupOrCourseMember(), null);
        }

        File zipFile = fileUploadSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFile);
    }

    /**
     * PUT /file-upload-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing fileUploadExercise.
     *
     * @param exerciseId                                   of the exercise
     * @param fileUploadExercise                           the fileUploadExercise to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate  boolean flag that indicates whether the associated feedback should be deleted or not
     *
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadExercise, or
     * with status 400 (Bad Request) if the fileUploadExercise is not valid, or with status 409 (Conflict)
     * if given exerciseId is not same as in the object of the request body, or with status 500 (Internal
     * Server Error) if the fileUploadExercise couldn't be updated
     */
    @PutMapping(Endpoints.REEVALUATE_EXERCISE)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<FileUploadExercise> reEvaluateAndUpdateFileUploadExercise(@PathVariable long exerciseId, @RequestBody FileUploadExercise fileUploadExercise,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate FileUploadExercise : {}", fileUploadExercise);

        // check that the exercise exists for given id
        fileUploadExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(exerciseId, fileUploadExercise);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);

        // Check that the user is authorized to update the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        exerciseService.reEvaluateExercise(fileUploadExercise, deleteFeedbackAfterGradingInstructionUpdate);
        return updateFileUploadExercise(fileUploadExercise, null, fileUploadExercise.getId());
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String FILE_UPLOAD_EXERCISES = "/file-upload-exercises";

        public static final String FILE_UPLOAD_EXERCISE = FILE_UPLOAD_EXERCISES + "/{exerciseId}";

        public static final String REEVALUATE_EXERCISE = FILE_UPLOAD_EXERCISE + "/re-evaluate";

        private Endpoints() {
        }
    }
}
