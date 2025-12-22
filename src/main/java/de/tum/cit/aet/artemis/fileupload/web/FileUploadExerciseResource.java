package de.tum.cit.aet.artemis.fileupload.web;

import static de.tum.cit.aet.artemis.core.config.Constants.FILE_ENDING_PATTERN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.TITLE_NAME_PATTERN;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
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
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExercisesDTO;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadExerciseImportService;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadExerciseService;
import de.tum.cit.aet.artemis.fileupload.service.FileUploadSubmissionExportService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;

/**
 * REST controller for managing FileUploadExercise.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/fileupload/")
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

    private final ExerciseVersionService exerciseVersionService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<SlideApi> slideApi;

    private final Optional<AtlasMLApi> atlasMLApi;

    private final Optional<CompetencyApi> competencyApi;

    public FileUploadExerciseResource(FileUploadExerciseRepository fileUploadExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService,
            FileUploadSubmissionExportService fileUploadSubmissionExportService, GradingCriterionRepository gradingCriterionRepository, CourseRepository courseRepository,
            ParticipationRepository participationRepository, GroupNotificationScheduleService groupNotificationScheduleService,
            FileUploadExerciseImportService fileUploadExerciseImportService, FileUploadExerciseService fileUploadExerciseService, ChannelService channelService,
            ExerciseVersionService exerciseVersionService, ChannelRepository channelRepository, Optional<CompetencyProgressApi> competencyProgressApi, Optional<SlideApi> slideApi,
            Optional<AtlasMLApi> atlasMLApi, Optional<CompetencyApi> competencyApi) {
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
        this.exerciseVersionService = exerciseVersionService;
        this.competencyProgressApi = competencyProgressApi;
        this.slideApi = slideApi;
        this.atlasMLApi = atlasMLApi;
        this.competencyApi = competencyApi;
    }

    /**
     * POST /file-upload-exercises : Create a new fileUploadExercise.
     *
     * @param fileUploadExercise the fileUploadExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new
     *         fileUploadExercise, or with status 400 (Bad Request) if the
     *         fileUploadExercise has already an ID
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
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(result));

        // Notify AtlasML about the new exercise
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(result, OperationTypeDTO.UPDATE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about exercise creation: {}", e.getMessage());
            }
        });

        exerciseVersionService.createExerciseVersion(result);

        return ResponseEntity.created(new URI("/api/fileupload/file-upload-exercises/" + result.getId())).body(result);
    }

    /**
     * POST file-upload-exercises/import: Imports an existing file upload exercise
     * into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates.
     * Referenced entities will get cloned and assigned a new id.
     * Uses {@link FileUploadExerciseImportService}.
     *
     * @param sourceId                   The ID of the original exercise which
     *                                       should get imported
     * @param importedFileUploadExercise The new exercise containing values that
     *                                       should get overwritten in the imported
     *                                       exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template
     *         does not exist, or a forbidden error
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

        // Notify AtlasML about the new exercise
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(newFileUploadExercise, OperationTypeDTO.UPDATE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about exercise creation: {}", e.getMessage());
            }
        });
        exerciseVersionService.createExerciseVersion(newFileUploadExercise);

        return ResponseEntity.created(new URI("/api/fileupload/file-upload-exercises/" + newFileUploadExercise.getId())).body(newFileUploadExercise);
    }

    private boolean isFilePatternValid(FileUploadExercise exercise) {
        // a file ending should consist of a comma separated list of 1-5 characters /
        // digits
        // when an empty string "" is passed in the exercise the file-pattern is null
        // when it arrives in the rest endpoint
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
     * Search for all file-upload exercises by id, title and course title. The
     * result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page
     *                           number and query string
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
     * @param updateFileUploadExercisesDTO the fileUploadExerciseDTO to update
     * @param notificationText             the text shown to students
     * @param exerciseId                   the id of exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated
     *         fileUploadExercise, or with status 400 (Bad Request) if the
     *         fileUploadExercise is not valid, or
     *         with status 500 (Internal Server Error) if the fileUploadExercise
     *         couldn't be updated
     */
    @PutMapping("file-upload-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<FileUploadExercise> updateFileUploadExercise(@RequestBody UpdateFileUploadExercisesDTO updateFileUploadExercisesDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText, @PathVariable Long exerciseId) {
        log.debug("REST request to update FileUploadExercise : {}", updateFileUploadExercisesDTO);
        // TODO: The route has an exerciseId but we don't do anything useful with it.
        // Change route and client requests?
        final var fileUploadExerciseBeforeUpdate = fileUploadExerciseRepository.findByIdWithCompetenciesAndGradingCriteria(exerciseId).orElseThrow();

        ZonedDateTime oldDueDate = fileUploadExerciseBeforeUpdate.getDueDate();
        ZonedDateTime oldAssessmentDueDate = fileUploadExerciseBeforeUpdate.getAssessmentDueDate();
        ZonedDateTime oldReleaseDate = fileUploadExerciseBeforeUpdate.getReleaseDate();
        Double oldMaxPoints = fileUploadExerciseBeforeUpdate.getMaxPoints();
        Double oldBonusPoints = fileUploadExerciseBeforeUpdate.getBonusPoints();
        String oldProblemStatement = fileUploadExerciseBeforeUpdate.getProblemStatement();

        // Retrieve the course over the exerciseGroup or the given courseId
        if (updateFileUploadExercisesDTO.courseId() == null) {
            throw new BadRequestAlertException("The courseId is required.", ENTITY_NAME, "courseIdMissing");
        }
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(fileUploadExerciseBeforeUpdate);
        if (!Objects.equals(course.getId(), updateFileUploadExercisesDTO.courseId())) {
            throw new BadRequestAlertException("The course can not be changed.", ENTITY_NAME, "courseIdInvalid");
        }

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        FileUploadExercise updatedFileUploadExercise = update(updateFileUploadExercisesDTO, fileUploadExerciseBeforeUpdate);
        // Validate the updated file upload exercise
        validateNewOrUpdatedFileUploadExercise(updatedFileUploadExercise);

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(updatedFileUploadExercise, fileUploadExerciseBeforeUpdate, ENTITY_NAME);

        channelService.updateExerciseChannel(fileUploadExerciseBeforeUpdate, updatedFileUploadExercise);

        var persistedExercise = exerciseService.saveWithCompetencyLinks(updatedFileUploadExercise, fileUploadExerciseRepository::save);
        exerciseService.logUpdate(persistedExercise, persistedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(oldMaxPoints, oldBonusPoints, persistedExercise);
        slideApi.ifPresent(api -> api.handleDueDateChange(oldDueDate, persistedExercise));
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(persistedExercise, oldDueDate);

        exerciseService.notifyAboutExerciseChanges(oldReleaseDate, oldAssessmentDueDate, oldProblemStatement, persistedExercise, notificationText);
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(fileUploadExerciseBeforeUpdate, Optional.of(persistedExercise)));

        // Notify AtlasML about the exercise update
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(persistedExercise, OperationTypeDTO.UPDATE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about exercise update: {}", e.getMessage());
            }
        });
        exerciseVersionService.createExerciseVersion(persistedExercise);

        return ResponseEntity.ok(persistedExercise);
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of
     *         fileUploadExercises in body
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
     * @return the ResponseEntity with status 200 (OK) and with body the
     *         fileUploadExercise, or with status 404 (Not Found)
     */
    @GetMapping("file-upload-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<FileUploadExercise> getFileUploadExercise(@PathVariable Long exerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get FileUploadExercise : {}", exerciseId);
        var exercise = fileUploadExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("FileUploadExercise", exerciseId));
        // If the exercise belongs to an exam, only editors or above are allowed to
        // access it, otherwise also TA have access
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
     * DELETE /file-upload-exercises/:exerciseId : delete the "id"
     * fileUploadExercise.
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

        // Notify AtlasML about the exercise deletion before actual deletion
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(exercise, OperationTypeDTO.DELETE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about exercise deletion: {}", e.getMessage());
            }
        });
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        // note: we use the exercise service here, because this one makes sure to clean
        // up all lazy references correctly.
        exerciseService.logDeletion(exercise, exercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exercise.getTitle())).build();
    }

    /**
     * POST /file-upload-exercises/:exerciseId/export-submissions : sends exercise
     * submissions as zip
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

        Path zipFilePath = fileUploadSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFilePath);
    }

    /**
     * PUT /file-upload-exercises/{exerciseId}/re-evaluate : Re-evaluates and
     * updates an existing fileUploadExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param updateFileUploadExercisesDTO                the fileUploadExerciseDTO to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that
     *                                                        indicates whether the
     *                                                        associated feedback should
     *                                                        be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated
     *         fileUploadExercise, or
     *         with status 400 (Bad Request) if the fileUploadExercise is not valid,
     *         or with status 409 (Conflict)
     *         if given exerciseId is not same as in the object of the request body,
     *         or with status 500 (Internal
     *         Server Error) if the fileUploadExercise couldn't be updated
     */
    @PutMapping("file-upload-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<FileUploadExercise> reEvaluateAndUpdateFileUploadExercise(@PathVariable long exerciseId,
            @RequestBody UpdateFileUploadExercisesDTO updateFileUploadExercisesDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate FileUploadExercise : {}", updateFileUploadExercisesDTO);

        final FileUploadExercise existingExercise = fileUploadExerciseRepository.findByIdWithCompetenciesAndGradingCriteria(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "FileUploadExercise not found"));

        // check that the exercise exists for given id
        authCheckService.checkGivenExerciseIdSameForExerciseRequestBodyIdElseThrow(exerciseId, updateFileUploadExercisesDTO.id());

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(existingExercise);

        // Check that the user is authorized to update the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
        FileUploadExercise exerciseForReevaluation = update(updateFileUploadExercisesDTO, existingExercise);

        exerciseService.reEvaluateExercise(exerciseForReevaluation, deleteFeedbackAfterGradingInstructionUpdate);
        return updateFileUploadExercise(updateFileUploadExercisesDTO, null, updateFileUploadExercisesDTO.id());
    }

    /**
     * Validate the fileUpload exercise title.
     * 1. Check presence and length of exercise title
     * 2. Find forbidden patterns in exercise title
     *
     * @param fileUploadExercise FileUpload exercise to be validated
     */
    private void validateTitle(FileUploadExercise fileUploadExercise) {
        // Check if exercise title is set
        if (fileUploadExercise.getTitle() == null || fileUploadExercise.getTitle().isBlank() || fileUploadExercise.getTitle().length() < 3) {
            throw new BadRequestAlertException("The title is not set or is too short.", ENTITY_NAME, "titleLengthInvalid");
        }
        // Check if the exercise title matches regex
        Matcher titleMatcher = TITLE_NAME_PATTERN.matcher(fileUploadExercise.getTitle());
        if (!titleMatcher.matches()) {
            throw new BadRequestAlertException("The title is invalid.", ENTITY_NAME, "titlePatternInvalid");
        }
    }

    /**
     * Replaces the grading criteria of the given exercise according to PUT semantics.
     * <p>
     * If {@code dto.gradingCriteria()} is {@code null} or empty, all existing criteria are removed (if initialized).
     * Otherwise, existing criteria are updated by id and new ones are created for DTOs without id.
     *
     * @param dto      the update DTO containing grading criteria
     * @param exercise the exercise to mutate
     */
    private void updateGradingCriteria(UpdateFileUploadExercisesDTO dto, FileUploadExercise exercise) {
        if (dto.gradingCriteria() == null || dto.gradingCriteria().isEmpty()) {
            clearInitializedCollection(exercise.getGradingCriteria());
            return;
        }

        Set<GradingCriterion> managedCriteria = ensureGradingCriteriaSet(exercise);

        Map<Long, GradingCriterion> existingById = managedCriteria.stream().filter(gc -> gc.getId() != null)
                .collect(Collectors.toMap(GradingCriterion::getId, gc -> gc, (a, b) -> a));

        Set<GradingCriterion> updated = dto.gradingCriteria().stream().map(gcDto -> {
            GradingCriterion criterion = (gcDto.id() != null) ? existingById.get(gcDto.id()) : null;
            if (criterion == null) {
                criterion = gcDto.toEntity();
                criterion.setExercise(exercise);
            }
            else {
                gcDto.applyTo(criterion);
            }
            return criterion;
        }).collect(Collectors.toSet());

        managedCriteria.clear();
        managedCriteria.addAll(updated);
    }

    /**
     * Ensures that the exercise has a mutable set for grading criteria.
     * Creates and assigns a new {@link HashSet} if the current set is {@code null}.
     *
     * @param exercise the exercise to mutate
     * @return the non-null mutable set of grading criteria
     */
    private Set<GradingCriterion> ensureGradingCriteriaSet(FileUploadExercise exercise) {
        Set<GradingCriterion> managedCriteria = exercise.getGradingCriteria();
        if (managedCriteria == null) {
            managedCriteria = new HashSet<>();
            exercise.setGradingCriteria(managedCriteria);
        }
        return managedCriteria;
    }

    /**
     * Replaces the competency links of the given exercise according to PUT semantics.
     * <p>
     * If {@code dto.competencyLinks()} is {@code null} or empty, all existing links are removed (if initialized).
     * Otherwise, weights are updated for existing links and missing links are created using managed competency references.
     *
     * <p>
     * <b>Hibernate note:</b> Uses {@code competencyRepository.getReferenceById(...)} to avoid creating detached entities
     * and to keep associations consistent with the persistence context.
     *
     * @param dto      the update DTO containing competency link updates
     * @param exercise the exercise to mutate
     * @throws BadRequestAlertException if a competency does not belong to the exercise's course
     */
    private void updateCompetencyLinks(UpdateFileUploadExercisesDTO dto, FileUploadExercise exercise) {
        if (dto.competencyLinks() == null || dto.competencyLinks().isEmpty()) {
            clearInitializedCollection(exercise.getCompetencyLinks());
            return;
        }
        CompetencyApi api = competencyApi.orElseThrow(() -> new BadRequestAlertException("Competency links require Atlas to be enabled.", "CourseCompetency", "atlasDisabled"));

        Set<CompetencyExerciseLink> managedLinks = ensureCompetencyLinksSet(exercise);

        Map<Long, CompetencyExerciseLink> existingByCompetencyId = managedLinks.stream().filter(link -> link.getCompetency() != null && link.getCompetency().getId() != null)
                .collect(Collectors.toMap(link -> link.getCompetency().getId(), link -> link, (a, b) -> a));

        Long exerciseCourseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;

        Set<CompetencyExerciseLink> updated = new HashSet<>();
        for (var linkDto : dto.competencyLinks()) {

            if (exerciseCourseId != null && linkDto.courseId() != null && !Objects.equals(exerciseCourseId, linkDto.courseId())) {
                throw new BadRequestAlertException("The competency does not belong to the exercise's course.", "CourseCompetency", "wrongCourse");
            }

            var competencyDto = linkDto.courseCompetencyDTO();
            Long competencyId = competencyDto.id();

            CompetencyExerciseLink link = existingByCompetencyId.get(competencyId);
            if (link == null) {
                Competency competencyRef = api.getReference(competencyId);
                validateCompetencyBelongsToExerciseCourse(exerciseCourseId, competencyRef);
                link = new CompetencyExerciseLink(competencyRef, exercise, linkDto.weight());
            }
            else {
                link.setWeight(linkDto.weight());
            }

            updated.add(link);
        }

        managedLinks.clear();
        managedLinks.addAll(updated);
    }

    /**
     * Ensures that the exercise has a mutable set for competency links.
     * Creates and assigns a new {@link HashSet} if the current set is {@code null}.
     *
     * @param exercise the exercise to mutate
     * @return the non-null mutable set of competency links
     */
    private Set<CompetencyExerciseLink> ensureCompetencyLinksSet(FileUploadExercise exercise) {
        Set<CompetencyExerciseLink> managedLinks = exercise.getCompetencyLinks();
        if (managedLinks == null) {
            managedLinks = new HashSet<>();
            exercise.setCompetencyLinks(managedLinks);
        }
        return managedLinks;
    }

    /**
     * Validates that the given competency belongs to the same course as the exercise.
     * If the exercise has no course (e.g. inconsistent state), this check is skipped.
     *
     * @param exerciseCourseId the course id of the exercise (maybe {@code null})
     * @param competency       a managed competency entity or reference
     * @throws BadRequestAlertException if the competency is associated with a different course
     */
    private void validateCompetencyBelongsToExerciseCourse(Long exerciseCourseId, Competency competency) {
        if (exerciseCourseId == null) {
            return;
        }
        var competencyCourse = competency.getCourse();
        Long competencyCourseId = competencyCourse != null ? competencyCourse.getId() : null;

        if (competencyCourseId != null && !Objects.equals(exerciseCourseId, competencyCourseId)) {
            throw new BadRequestAlertException("The competency does not belong to the exercise's course.", "CourseCompetency", "wrongCourse");
        }
    }

    /**
     * Clears the given collection if it is initialized.
     * <p>
     * This avoids triggering lazy initialization in callers that do not fetch the collection.
     * In this service, callers typically load the exercise with the required associations eagerly.
     *
     * @param set the set to clear
     * @param <T> element type
     */
    private static <T> void clearInitializedCollection(Set<T> set) {
        if (set != null && Hibernate.isInitialized(set)) {
            set.clear();
        }
    }

    /**
     * Applies new updateFileUploadExercise's data to the given exercise, mutating it in place.
     * <p>
     * This method follows PUT semantics:
     * <ul>
     * <li>All fields in the DTO represent the new state.</li>
     * <li>Required attributes (e.g. title) are validated here and must not be {@code null} or blank.</li>
     * <li>Nullable attributes are explicitly overwritten, i.e. {@code null} means "clear existing value".</li>
     * <li>Collections (grading criteria, competency links) are fully replaced; {@code null} or empty means "remove all".</li>
     * </ul>
     *
     * @param updateFileUploadExercisesDTO the DTO containing the updated state for the exercise
     * @param exercise                     the exercise to update (will be mutated)
     * @return the same {@link FileUploadExercise} instance after applying the updates
     * @throws BadRequestAlertException if required fields are missing/invalid or a competency from the DTO
     *                                      does not belong to the exercise's course or otherwise violates domain constraints
     */
    private FileUploadExercise update(UpdateFileUploadExercisesDTO updateFileUploadExercisesDTO, FileUploadExercise exercise) {
        if (updateFileUploadExercisesDTO == null) {
            throw new BadRequestAlertException("No fileUpload exercise was provided.", ENTITY_NAME, "isNull");
        }
        exercise.setTitle(updateFileUploadExercisesDTO.title());
        validateTitle(exercise);
        exercise.setShortName(updateFileUploadExercisesDTO.shortName());
        // problemStatement: null → empty string
        String newProblemStatement = updateFileUploadExercisesDTO.problemStatement() == null ? "" : updateFileUploadExercisesDTO.problemStatement();
        exercise.setProblemStatement(newProblemStatement);

        exercise.setChannelName(updateFileUploadExercisesDTO.channelName());
        exercise.setCategories(updateFileUploadExercisesDTO.categories());
        exercise.setDifficulty(updateFileUploadExercisesDTO.difficulty());

        exercise.setMaxPoints(updateFileUploadExercisesDTO.maxPoints());
        exercise.setBonusPoints(updateFileUploadExercisesDTO.bonusPoints());
        exercise.setIncludedInOverallScore(updateFileUploadExercisesDTO.includedInOverallScore());

        exercise.setReleaseDate(updateFileUploadExercisesDTO.releaseDate());
        exercise.setStartDate(updateFileUploadExercisesDTO.startDate());
        exercise.setDueDate(updateFileUploadExercisesDTO.dueDate());
        exercise.setAssessmentDueDate(updateFileUploadExercisesDTO.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(updateFileUploadExercisesDTO.exampleSolutionPublicationDate());

        // validates general settings: points, dates
        exercise.validateGeneralSettings();

        exercise.setAllowComplaintsForAutomaticAssessments(updateFileUploadExercisesDTO.allowComplaintsForAutomaticAssessments());
        exercise.setAllowFeedbackRequests(updateFileUploadExercisesDTO.allowFeedbackRequests());
        exercise.setPresentationScoreEnabled(updateFileUploadExercisesDTO.presentationScoreEnabled());
        exercise.setSecondCorrectionEnabled(updateFileUploadExercisesDTO.secondCorrectionEnabled());
        exercise.setFeedbackSuggestionModule(updateFileUploadExercisesDTO.feedbackSuggestionModule());
        exercise.setGradingInstructions(updateFileUploadExercisesDTO.gradingInstructions());

        exercise.setExampleSolution(updateFileUploadExercisesDTO.exampleSolution());
        exercise.setFilePattern(updateFileUploadExercisesDTO.filePattern());

        updateGradingCriteria(updateFileUploadExercisesDTO, exercise);
        updateCompetencyLinks(updateFileUploadExercisesDTO, exercise);

        return exercise;
    }
}
