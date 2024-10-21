package de.tum.cit.aet.artemis.fileupload.web;

import static de.tum.cit.aet.artemis.core.config.Constants.FILE_ENDING_PATTERN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.CourseService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadExerciseImportService;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadExerciseService;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadSubmissionExportService;

/**
 * REST controller for managing FileUploadExercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class FileUploadExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(FileUploadExerciseResource.class);

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

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final FileUploadSubmissionExportService fileUploadSubmissionExportService;

    private final FileUploadExerciseImportService fileUploadExerciseImportService;

    private final FileUploadExerciseService fileUploadExerciseService;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final CompetencyProgressService competencyProgressService;

    public FileUploadExerciseResource(FileUploadExerciseRepository fileUploadExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService,
            FileUploadSubmissionExportService fileUploadSubmissionExportService, GradingCriterionRepository gradingCriterionRepository, CourseRepository courseRepository,
            ParticipationRepository participationRepository, GroupNotificationScheduleService groupNotificationScheduleService,
            FileUploadExerciseImportService fileUploadExerciseImportService, FileUploadExerciseService fileUploadExerciseService, ChannelService channelService,
            ChannelRepository channelRepository, CompetencyProgressService competencyProgressService) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.fileUploadSubmissionExportService = fileUploadSubmissionExportService;
        this.courseRepository = courseRepository;
        this.participationRepository = participationRepository;
        this.fileUploadExerciseImportService = fileUploadExerciseImportService;
        this.fileUploadExerciseService = fileUploadExerciseService;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * POST /file-upload-exercises : Create a new fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("file-upload-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<FileUploadExercise> createFileUploadExercise(@RequestBody FileUploadExercise fileUploadExercise) throws URISyntaxException {
        log.debug("REST request to save FileUploadExercise : {}", fileUploadExercise);
        if (fileUploadExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "A new fileUploadExercise cannot already have an ID", "idExists")).body(null);
        }
        // validates general settings: points, dates
        fileUploadExercise.validateGeneralSettings();
        // Validate the new file upload exercise
        validateNewOrUpdatedFileUploadExercise(fileUploadExercise);
        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExercise);
        // Check that the user is authorized to create the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        FileUploadExercise result = exerciseService.saveWithCompetencyLinks(fileUploadExercise, fileUploadExerciseRepository::save);

        channelService.createExerciseChannel(result, Optional.ofNullable(fileUploadExercise.getChannelName()));
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(fileUploadExercise);
        competencyProgressService.updateProgressByLearningObjectAsync(result);

        return ResponseEntity.created(new URI("/api/file-upload-exercises/" + result.getId())).body(result);
    }

    /**
     * POST file-upload-exercises/import: Imports an existing file upload exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates.
     * Referenced entities will get cloned and assigned a new id.
     * Uses {@link FileUploadExerciseImportService}.
     *
     * @param sourceId                   The ID of the original exercise which should get imported
     * @param importedFileUploadExercise The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist, or a forbidden error
     *         (403) if the user is not at least an editor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("file-upload-exercises/import/{sourceId}")
    @EnforceAtLeastEditor
    public ResponseEntity<FileUploadExercise> importFileUploadExercise(@PathVariable long sourceId, @RequestBody FileUploadExercise importedFileUploadExercise)
            throws URISyntaxException {

        if (sourceId <= 0 || (importedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedFileUploadExercise.getExerciseGroup() == null)) {
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedFileUploadExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalFileUploadExercise = fileUploadExerciseRepository.findByIdElseThrow(sourceId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedFileUploadExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalFileUploadExercise, user);
        // validates general settings: points, dates, exam score included completely
        importedFileUploadExercise.validateGeneralSettings();

        final var newFileUploadExercise = fileUploadExerciseImportService.importFileUploadExercise(originalFileUploadExercise, importedFileUploadExercise);
        return ResponseEntity.created(new URI("/api/file-upload-exercises/" + newFileUploadExercise.getId())).body(newFileUploadExercise);
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
     * Search for all file-upload exercises by id, title and course title. The result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("file-upload-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<FileUploadExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") Boolean isCourseFilter, @RequestParam(defaultValue = "true") Boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(fileUploadExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }

    /**
     * PUT /file-upload-exercises : Updates an existing fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to update
     * @param notificationText   the text shown to students
     * @param exerciseId         the id of exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadExercise, or with status 400 (Bad Request) if the fileUploadExercise is not valid, or
     *         with status 500 (Internal Server Error) if the fileUploadExercise couldn't be updated
     */
    @PutMapping("file-upload-exercises/{exerciseId}")
    @EnforceAtLeastEditor
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
        final var fileUploadExerciseBeforeUpdate = fileUploadExerciseRepository.findWithEagerCompetenciesByIdElseThrow(fileUploadExercise.getId());

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(fileUploadExercise, fileUploadExerciseBeforeUpdate, ENTITY_NAME);

        channelService.updateExerciseChannel(fileUploadExerciseBeforeUpdate, fileUploadExercise);

        var updatedExercise = exerciseService.saveWithCompetencyLinks(fileUploadExercise, fileUploadExerciseRepository::save);
        exerciseService.logUpdate(updatedExercise, updatedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(fileUploadExerciseBeforeUpdate, updatedExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedExercise, fileUploadExerciseBeforeUpdate.getDueDate());

        exerciseService.notifyAboutExerciseChanges(fileUploadExerciseBeforeUpdate, updatedExercise, notificationText);
        competencyProgressService.updateProgressForUpdatedLearningObjectAsync(fileUploadExerciseBeforeUpdate, Optional.of(fileUploadExercise));

        return ResponseEntity.ok(updatedExercise);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of fileUploadExercises in body
     */
    @GetMapping(value = "courses/{courseId}/file-upload-exercises")
    @EnforceAtLeastTutor
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
    @GetMapping("file-upload-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long exerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get FileUploadExercise : {}", exerciseId);
        var exercise = fileUploadExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("FileUploadExercise", exerciseId));
        // If the exercise belongs to an exam, only editors or above are allowed to access it, otherwise also TA have access
        if (exercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        }
        else {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        }

        if (exercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(exercise.getId());
            if (channel != null) {
                exercise.setChannelName(channel.getName());
            }
        }

        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
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
    @DeleteMapping("file-upload-exercises/{exerciseId}")
    @EnforceAtLeastInstructor
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
     * @param exerciseId              the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("file-upload-exercises/{exerciseId}/export-submissions")
    @EnforceAtLeastTutor
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
     * @param exerciseId                                  of the exercise
     * @param fileUploadExercise                          the fileUploadExercise to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated fileUploadExercise, or
     *         with status 400 (Bad Request) if the fileUploadExercise is not valid, or with status 409 (Conflict)
     *         if given exerciseId is not same as in the object of the request body, or with status 500 (Internal
     *         Server Error) if the fileUploadExercise couldn't be updated
     */
    @PutMapping("file-upload-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
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
}
