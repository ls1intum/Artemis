package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.FILE_ENDING_PATTERN;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

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
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.user.UserRetrievalService;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/** REST controller for managing FileUploadExercise. */
@RestController
@RequestMapping("/api")
public class FileUploadExerciseResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadExerciseResource.class);

    private static final String ENTITY_NAME = "fileUploadExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FileUploadExerciseService fileUploadExerciseService;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ExerciseService exerciseService;

    private final UserRetrievalService userRetrievalService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final GradingCriterionService gradingCriterionService;

    private final ExerciseGroupService exerciseGroupService;

    private final FileUploadSubmissionExportService fileUploadSubmissionExportService;

    public FileUploadExerciseResource(FileUploadExerciseService fileUploadExerciseService, FileUploadExerciseRepository fileUploadExerciseRepository,
            UserRetrievalService userRetrievalService, AuthorizationCheckService authCheckService, CourseService courseService, GroupNotificationService groupNotificationService,
            ExerciseService exerciseService, FileUploadSubmissionExportService fileUploadSubmissionExportService, GradingCriterionService gradingCriterionService,
            ExerciseGroupService exerciseGroupService) {
        this.fileUploadExerciseService = fileUploadExerciseService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userRetrievalService = userRetrievalService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
        this.gradingCriterionService = gradingCriterionService;
        this.exerciseGroupService = exerciseGroupService;
        this.fileUploadSubmissionExportService = fileUploadSubmissionExportService;
    }

    /**
     * POST /file-upload-exercises : Create a new fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/file-upload-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadExercise> createFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to save FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "A new fileUploadExercise cannot already have an ID", "idexists")).body(null);
        }

        // Validate score settings
        Optional<ResponseEntity<FileUploadExercise>> optionalScoreSettingsError = exerciseService.validateScoreSettings(fileUploadExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError.get();
        }

        // Validate the new file upload exercise
        validateNewOrUpdatedFileUploadExercise(fileUploadExercise);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);

        // Check that the user is authorized to create the exercise
        User user = userRetrievalService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

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
        var filePattern = exercise.getFilePattern().toLowerCase().replaceAll("\\s+", "");
        var allowedFileEndings = filePattern.split(",");
        if (allowedFileEndings.length == 0) {
            return false;
        }
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
        exerciseService.checkCourseAndExerciseGroupExclusivity(fileUploadExercise, ENTITY_NAME);

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
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadExercise> updateFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText, @PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to update FileUploadExercise : {}", fileUploadExercise);

        // TODO: The route has an exerciseId but we don't do anything useful with it. Change route and client requests?

        // Validate the updated file upload exercise
        validateNewOrUpdatedFileUploadExercise(fileUploadExercise);

        // Validate score settings
        Optional<ResponseEntity<FileUploadExercise>> optionalScoreSettingsError = exerciseService.validateScoreSettings(fileUploadExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError.get();
        }

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);

        // Check that the user is authorized to update the exercise
        User user = userRetrievalService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        FileUploadExercise fileUploadExerciseBeforeUpdate = fileUploadExerciseService.findOne(fileUploadExercise.getId());

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(fileUploadExercise, fileUploadExerciseBeforeUpdate, ENTITY_NAME);

        FileUploadExercise result = fileUploadExerciseRepository.save(fileUploadExercise);

        // Only notify students about changes if a regular exercise was updated
        if (notificationText != null && fileUploadExercise.isCourseExercise()) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(fileUploadExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).body(result);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of fileUploadExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/file-upload-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<FileUploadExercise>> getFileUploadExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userRetrievalService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        List<FileUploadExercise> exercises = fileUploadExerciseRepository.findByCourseId(courseId);
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
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long exerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get FileUploadExercise : {}", exerciseId);
        Optional<FileUploadExercise> optionalFileUploadExercise = fileUploadExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesById(exerciseId);

        if (optionalFileUploadExercise.isEmpty()) {
            return notFound();
        }
        FileUploadExercise fileUploadExercise = optionalFileUploadExercise.get();

        // If the exercise belongs to an exam, only instructors and admins are allowed to access it, otherwise also TA have access
        if (fileUploadExercise.isExamExercise()) {
            // Get the course over the exercise group
            ExerciseGroup exerciseGroup = exerciseGroupService.findOneWithExam(fileUploadExercise.getExerciseGroup().getId());
            Course course = exerciseGroup.getExam().getCourse();

            if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
                return forbidden();
            }
            // Set the exerciseGroup, exam and course so that the client can work with those ids
            fileUploadExercise.setExerciseGroup(exerciseGroup);
        }
        else if (!authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise)) {
            return forbidden();
        }

        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        return ResponseEntity.ok().body(fileUploadExercise);
    }

    /**
     * DELETE /file-upload-exercises/:exerciseId : delete the "id" fileUploadExercise.
     *
     * @param exerciseId the id of the fileUploadExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/file-upload-exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteFileUploadExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete FileUploadExercise : {}", exerciseId);
        Optional<FileUploadExercise> optionalFileUploadExercise = fileUploadExerciseRepository.findById(exerciseId);
        if (optionalFileUploadExercise.isEmpty()) {
            return notFound();
        }
        FileUploadExercise fileUploadExercise = optionalFileUploadExercise.get();

        // If the exercise belongs to an exam, the course must be retrieved over the exerciseGroup
        Course course;
        if (fileUploadExercise.isExamExercise()) {
            course = exerciseGroupService.retrieveCourseOverExerciseGroup(fileUploadExercise.getExerciseGroup().getId());
        }
        else {
            course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();
        }

        User user = userRetrievalService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(fileUploadExercise, course, user);
        exerciseService.delete(exerciseId, false, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, fileUploadExercise.getTitle())).build();
    }

    /**
     * POST /file-upload-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("/file-upload-exercises/{exerciseId}/export-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {

        Optional<FileUploadExercise> optionalFileUploadExercise = fileUploadExerciseRepository.findById(exerciseId);
        if (optionalFileUploadExercise.isEmpty()) {
            return notFound();
        }

        FileUploadExercise fileUploadExercise = optionalFileUploadExercise.get();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise)) {
            return forbidden();
        }

        // ta's are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants()
                && !authCheckService.isAtLeastInstructorInCourse(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember(), null)) {
            return forbidden();
        }

        try {
            Optional<File> zipFile = fileUploadSubmissionExportService.exportStudentSubmissions(exerciseId, submissionExportOptions);

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
}
