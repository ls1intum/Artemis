package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.FILE_ENDING_PATTERN;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

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
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
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

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final FileUploadSubmissionExportService fileUploadSubmissionExportService;

    public FileUploadExerciseResource(FileUploadExerciseRepository fileUploadExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, GroupNotificationService groupNotificationService, ExerciseService exerciseService,
            FileUploadSubmissionExportService fileUploadSubmissionExportService, GradingCriterionRepository gradingCriterionRepository,
            ExerciseGroupRepository exerciseGroupRepository, CourseRepository courseRepository) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.fileUploadSubmissionExportService = fileUploadSubmissionExportService;
        this.courseRepository = courseRepository;
    }

    /**
     * POST file-upload-exercises : Create a new File Upload Exercise.
     *
     * @param fileUploadExercise the File Upload Exercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new File Upload Exercise, or with status 400 (Bad Request) if the fileUploadExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("file-upload-exercises")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<FileUploadExercise> createFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to save File Upload Exercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "A new File Upload Exercise cannot already have an ID", "idExists")).body(null);
        }

        // validates general settings: points, dates
        exerciseService.validateGeneralSettings(fileUploadExercise);

        // Validate the new file upload exercise
        validateNewOrUpdatedFileUploadExercise(fileUploadExercise);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);

        // Check that the user is authorized to create the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);

        // Only notify tutors when the exercise is created for a course
        if (fileUploadExercise.isCourseExercise()) {
            groupNotificationService.notifyTutorGroupAboutExerciseCreated(fileUploadExercise);
        }
        return ResponseEntity.created(new URI("/api/file-upload-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
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
                    ENTITY_NAME, "filePattern.invalid");
        }
    }

    /**
     * PUT file-upload-exercises : Updates an existing File Upload Exercise.
     *
     * @param fileUploadExercise the File Upload Exercise to update
     * @param notificationText the text shown to students
     * @param exerciseId the id of exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated File Upload Exercise, or with status 400 (Bad Request) if the File Upload Exercise is not valid, or
     *         with status 500 (Internal Server Error) if the fileUploadExercise couldn't be updated
     */
    @PutMapping("file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<FileUploadExercise> updateFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText, @PathVariable Long exerciseId) {
        log.debug("REST request to update FileUploadExercise : {}", fileUploadExercise);

        if (fileUploadExercise.getId() == null || !fileUploadExercise.getId().equals(exerciseId)) {
            return badRequest("FileUploadExerciseID", "400", "exerciseId in path doesn't match the exerciseId in the Body!");
        }

        // Validate the updated file upload exercise
        validateNewOrUpdatedFileUploadExercise(fileUploadExercise);
        // validates general settings: points, dates
        exerciseService.validateGeneralSettings(fileUploadExercise);

        // Check that the user is authorized to update the exercise
        FileUploadExercise fileUploadExerciseBeforeUpdate = fileUploadExerciseRepository.findOneByIdElseThrow(fileUploadExercise.getId());
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, fileUploadExerciseBeforeUpdate, user);

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(fileUploadExercise, fileUploadExerciseBeforeUpdate, ENTITY_NAME);

        FileUploadExercise updatedExercise = fileUploadExerciseRepository.save(fileUploadExercise);
        exerciseService.logUpdate(updatedExercise, updatedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(fileUploadExerciseBeforeUpdate, updatedExercise);

        if ((notificationText != null && fileUploadExercise.isCourseExercise()) || fileUploadExercise.isExamExercise()) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(fileUploadExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).body(updatedExercise);
    }

    /**
     * GET courses/:courseId/file-upload-exercises : get all File Upload Exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Exercises in body
     */
    @GetMapping("courses/{courseId}/file-upload-exercises")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<FileUploadExercise>> getFileUploadExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<FileUploadExercise> exercises = fileUploadExerciseRepository.findByCourseId(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET file-upload-exercises/:exerciseId : get the File Upload Exercise with id exerciseId.
     *
     * @param exerciseId the id of the File Upload Exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the File Upload Exercise, or with status 404 (Not Found)
     */
    @GetMapping("file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long exerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get FileUploadExercise : {}", exerciseId);
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesByIdElseThrow(exerciseId);

        // If the exercise belongs to an exam, only instructors and admins are allowed to access it, otherwise also TA have access
        if (fileUploadExercise.isExamExercise()) {
            // Get the course over the exercise group
            ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdElseThrow(fileUploadExercise.getExerciseGroup().getId());
            Course course = exerciseGroup.getExam().getCourse();

            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
            // Set the exerciseGroup, exam and course so that the client can work with those ids
            fileUploadExercise.setExerciseGroup(exerciseGroup);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, fileUploadExercise, null);
        }

        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, fileUploadExercise);

        return ResponseEntity.ok().body(fileUploadExercise);
    }

    /**
     * DELETE file-upload-exercises/:exerciseId : delete the "id" fileUploadExercise.
     *
     * @param exerciseId the id of the fileUploadExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteFileUploadExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete FileUploadExercise : {}", exerciseId);
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findOneByIdElseThrow(exerciseId);

        // If the exercise belongs to an exam, the course must be retrieved over the exerciseGroup
        Course course;
        if (fileUploadExercise.isExamExercise()) {
            course = exerciseGroupRepository.retrieveCourseOverExerciseGroup(fileUploadExercise.getExerciseGroup().getId());
        }
        else {
            course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(fileUploadExercise, course, user);
        exerciseService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, fileUploadExercise.getTitle())).build();
    }

    /**
     * POST file-upload-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("file-upload-exercises/{exerciseId}/export-submissions")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {

        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findOneByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, fileUploadExercise, null);

        // TAs are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants()) {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, fileUploadExercise.getCourseViaExerciseGroupOrCourseMember(), null);
        }

        File zipFile = fileUploadSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFile);
    }

    /**
     * PUT file-upload-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing fileUploadExercise.
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
        fileUploadExerciseRepository.findOneByIdElseThrow(exerciseId);

        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(exerciseId, fileUploadExercise);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(fileUploadExercise, deleteFeedbackAfterGradingInstructionUpdate);

        return updateFileUploadExercise(fileUploadExercise, null, fileUploadExercise.getId());
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String FILE_UPLOAD_EXERCISES = "file-upload-exercises";

        public static final String FILE_UPLOAD_EXERCISE = FILE_UPLOAD_EXERCISES + "/{exerciseId}";

        public static final String REEVALUATE_EXERCISE = FILE_UPLOAD_EXERCISE + "/re-evaluate";

        private Endpoints() {
        }
    }
}
