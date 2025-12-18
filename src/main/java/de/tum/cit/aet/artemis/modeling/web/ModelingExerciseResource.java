package de.tum.cit.aet.artemis.modeling.web;

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
import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
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
import de.tum.cit.aet.artemis.exercise.service.SubmissionExportService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseImportService;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseService;

/**
 * REST controller for managing ModelingExercise.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/modeling/")
public class ModelingExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(ModelingExerciseResource.class);

    private static final String ENTITY_NAME = "modelingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final CourseService courseService;

    private final ParticipationRepository participationRepository;

    private final AuthorizationCheckService authCheckService;

    private final ModelingExerciseService modelingExerciseService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ModelingExerciseImportService modelingExerciseImportService;

    private final SubmissionExportService modelingSubmissionExportService;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final CompetencyRepository competencyRepository;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<SlideApi> slideApi;

    private final Optional<AtlasMLApi> atlasMLApi;

    public ModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository, UserRepository userRepository, CourseService courseService,
            AuthorizationCheckService authCheckService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ModelingExerciseService modelingExerciseService, ExerciseDeletionService exerciseDeletionService, ModelingExerciseImportService modelingExerciseImportService,
            SubmissionExportService modelingSubmissionExportService, ExerciseService exerciseService, GroupNotificationScheduleService groupNotificationScheduleService,
            GradingCriterionRepository gradingCriterionRepository, CompetencyRepository competencyRepository, ChannelService channelService, ChannelRepository channelRepository,
            ExerciseVersionService exerciseVersionService, Optional<CompetencyProgressApi> competencyProgressApi, Optional<SlideApi> slideApi, Optional<AtlasMLApi> atlasMLApi) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.courseService = courseService;
        this.modelingExerciseService = modelingExerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.modelingSubmissionExportService = modelingSubmissionExportService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.participationRepository = participationRepository;
        this.authCheckService = authCheckService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.exerciseService = exerciseService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.competencyRepository = competencyRepository;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.exerciseVersionService = exerciseVersionService;
        this.competencyProgressApi = competencyProgressApi;
        this.slideApi = slideApi;
        this.atlasMLApi = atlasMLApi;
    }

    // TODO: most of these calls should be done in the context of a course

    /**
     * POST modeling-exercises : Create a new modelingExercise.
     *
     * @param modelingExercise the modelingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new
     *         modelingExercise, or with status 400 (Bad Request) if the
     *         modelingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    // TODO: we should add courses/{courseId} here
    @PostMapping("modeling-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ModelingExercise> createModelingExercise(@RequestBody ModelingExercise modelingExercise) throws URISyntaxException {
        log.debug("REST request to save ModelingExercise : {}", modelingExercise);
        if (modelingExercise.getId() != null) {
            throw new BadRequestAlertException("A new modeling exercise cannot already have an ID", ENTITY_NAME, "idExists");
        }
        if (modelingExercise.getTitle() == null) {
            throw new BadRequestAlertException("A new modeling exercise needs a title", ENTITY_NAME, "missingtitle");
        }
        // validates general settings: points, dates
        modelingExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        modelingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(modelingExercise);
        // Check that the user is authorized to create the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        ModelingExercise result = exerciseService.saveWithCompetencyLinks(modelingExercise, modelingExerciseRepository::save);

        channelService.createExerciseChannel(result, Optional.ofNullable(modelingExercise.getChannelName()));
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(modelingExercise);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(result));

        // Notify AtlasML about the new modeling exercise
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(result, OperationTypeDTO.UPDATE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about modeling exercise creation: {}", e.getMessage());
            }
        });
        exerciseVersionService.createExerciseVersion(result);

        return ResponseEntity.created(new URI("/api/modeling-exercises/" + result.getId())).body(result);
    }

    /**
     * Search for all modeling exercises by id, title and course title. The result
     * is pageable since there might be hundreds
     * of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page
     *                           number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("modeling-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<ModelingExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(modelingExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }

    /**
     * PUT modeling-exercises : Updates an existing modelingExercise.
     *
     * @param updateModelingExerciseDTO the modelingExercise to update
     * @param notificationText          the text shown to students
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingExercise, or with status 400 (Bad Request) if the modelingExercise is not valid, or with
     *         status 500 (Internal Server Error) if the modelingExercise couldn't be updated
     */
    // NOTE: IMPORTANT we should NEVER call save on an entity retrieved from the client because it is unsafe and can lead to data loss
    @PutMapping("modeling-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ModelingExercise> updateModelingExercise(@RequestBody UpdateModelingExerciseDTO updateModelingExerciseDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ModelingExercise : {}", updateModelingExerciseDTO);

        final ModelingExercise originalExercise = modelingExerciseRepository
                .findByIdWithExampleSubmissionsResultsCompetenciesAndGradingCriteriaElseThrow(updateModelingExerciseDTO.id());

        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Important: use the original exercise for permission check
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalExercise, user);
        // Forbid changing the course the exercise belongs to.
        if (updateModelingExerciseDTO.courseId() == null) {
            throw new BadRequestAlertException("The courseId is required.", ENTITY_NAME, "courseIdMissing");
        }
        if (!Objects.equals(originalExercise.getCourseViaExerciseGroupOrCourseMember().getId(), updateModelingExerciseDTO.courseId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "forbidChangeCourseId");
        }

        ZonedDateTime oldDueDate = originalExercise.getDueDate();
        ZonedDateTime oldAssessmentDueDate = originalExercise.getAssessmentDueDate();
        ZonedDateTime oldReleaseDate = originalExercise.getReleaseDate();
        Double oldMaxPoints = originalExercise.getMaxPoints();
        Double oldBonusPoints = originalExercise.getBonusPoints();
        String oldProblemStatement = originalExercise.getProblemStatement();

        // whether is exam exercise or course exercise are not changeable
        ModelingExercise updatedExercise = update(updateModelingExerciseDTO, originalExercise);
        // Valid exercises have set either a course or an exerciseGroup
        updatedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(updatedExercise, originalExercise, ENTITY_NAME);

        channelService.updateExerciseChannel(originalExercise, updatedExercise);

        ModelingExercise persistedExercise = exerciseService.saveWithCompetencyLinks(updatedExercise, modelingExerciseRepository::save);

        exerciseService.logUpdate(updatedExercise, updatedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(oldMaxPoints, oldBonusPoints, persistedExercise);

        participationRepository.removeIndividualDueDatesIfBeforeDueDate(persistedExercise, oldDueDate);
        exerciseService.checkExampleSubmissions(persistedExercise);

        exerciseService.notifyAboutExerciseChanges(oldReleaseDate, oldAssessmentDueDate, oldProblemStatement, persistedExercise, notificationText);
        slideApi.ifPresent(api -> api.handleDueDateChange(oldDueDate, persistedExercise));

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(originalExercise, Optional.of(persistedExercise)));

        // Notify AtlasML about the modeling exercise update
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(persistedExercise, OperationTypeDTO.UPDATE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about modeling exercise update: {}", e.getMessage());
            }
        });

        exerciseVersionService.createExerciseVersion(persistedExercise);
        return ResponseEntity.ok(persistedExercise);
    }

    /**
     * GET /courses/:courseId/modeling-exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of
     *         modelingExercises in body
     */
    @GetMapping("courses/{courseId}/modeling-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ModelingExercise>> getModelingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ModelingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<ModelingExercise> exercises = modelingExerciseRepository.findByCourseIdWithCategories(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET modeling-exercises/:exerciseId : get the "id" modelingExercise.
     *
     * @param exerciseId the id of the modelingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the
     *         modelingExercise, or with status 404 (Not Found)
     */
    @GetMapping("modeling-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<ModelingExercise> getModelingExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ModelingExercise : {}", exerciseId);
        var modelingExercise = modelingExerciseRepository.findWithEagerExampleSubmissionsAndCompetenciesByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, modelingExercise, null);
        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        modelingExercise.setGradingCriteria(gradingCriteria);

        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, modelingExercise);

        if (modelingExercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(modelingExercise.getId());
            if (channel != null) {
                modelingExercise.setChannelName(channel.getName());
            }
        }

        return ResponseEntity.ok().body(modelingExercise);
    }

    /**
     * DELETE modeling-exercises/:id : delete the "id" modelingExercise.
     *
     * @param exerciseId the id of the modelingExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("modeling-exercises/{exerciseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteModelingExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete ModelingExercise : {}", exerciseId);
        var modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        // Notify AtlasML about the modeling exercise deletion before actual deletion
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(modelingExercise, OperationTypeDTO.DELETE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about modeling exercise deletion: {}", e.getMessage());
            }
        });
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, modelingExercise, user);
        // note: we use the exercise service here, because this one makes sure to clean
        // up all lazy references correctly.
        exerciseService.logDeletion(modelingExercise, modelingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, modelingExercise.getTitle())).build();
    }

    /**
     * POST modeling-exercises/import: Imports an existing modeling exercise into an
     * existing course
     * <p>
     * This will import the whole exercise except for the participations and Dates.
     * Referenced entities will get cloned and assigned a new id.
     * Uses {@link ModelingExerciseImportService}.
     *
     * @param sourceExerciseId The ID of the original exercise which should get
     *                             imported
     * @param importedExercise The new exercise containing values that should get
     *                             overwritten in the imported exercise, s.a. the title
     *                             or difficulty
     * @return The imported exercise (200), a not found error (404) if the template
     *         does not exist, or a forbidden error
     *         (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("modeling-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<ModelingExercise> importExercise(@PathVariable long sourceExerciseId, @RequestBody ModelingExercise importedExercise) throws URISyntaxException {
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalModelingExercise = modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalModelingExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();

        final var newModelingExercise = modelingExerciseImportService.importModelingExercise(originalModelingExercise, importedExercise);
        modelingExerciseRepository.save(newModelingExercise);
        // Notify AtlasML about the imported exercise
        atlasMLApi.ifPresent(api -> api.saveExerciseWithCompetencies(newModelingExercise));
        exerciseVersionService.createExerciseVersion(newModelingExercise, user);

        return ResponseEntity.created(new URI("/api/modeling-exercises/" + newModelingExercise.getId())).body(newModelingExercise);
    }

    /**
     * POST modeling-exercises/:exerciseId/export-submissions : sends exercise
     * submissions as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("modeling-exercises/{exerciseId}/export-submissions")
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, modelingExercise, null);

        // TAs are not allowed to download all participations
        if (submissionExportOptions.isExportAllParticipants()) {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, modelingExercise.getCourseViaExerciseGroupOrCourseMember(), null);
        }

        Path zipFilePath = modelingSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFilePath);
    }

    /**
     * PUT modeling-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an
     * existing modelingExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param updateModelingExerciseDTO                   the modelingExercise to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingExercise, or
     *         with status 400 (Bad Request) if the modelingExercise is not valid, or with status 409 (Conflict)
     *         if given exerciseId is not same as in the object of the request body, or with status 500 (Internal
     *         Server Error) if the modelingExercise couldn't be updated
     */
    @PutMapping("modeling-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<ModelingExercise> reEvaluateAndUpdateModelingExercise(@PathVariable long exerciseId, @RequestBody UpdateModelingExerciseDTO updateModelingExerciseDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate ModelingExercise : {}", updateModelingExerciseDTO);

        final ModelingExercise existingExercise = modelingExerciseRepository.findByIdWithExampleSubmissionsResultsCompetenciesAndGradingCriteriaElseThrow(exerciseId);
        authCheckService.checkGivenExerciseIdSameForExerciseRequestBodyIdElseThrow(exerciseId, updateModelingExerciseDTO.id());

        var user = userRepository.getUserWithGroupsAndAuthorities();
        // make sure the course actually exists
        ModelingExercise exerciseForReevaluation = update(updateModelingExerciseDTO, existingExercise);
        var course = courseRepository.findByIdElseThrow(exerciseForReevaluation.getCourseViaExerciseGroupOrCourseMember().getId());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(exerciseForReevaluation, deleteFeedbackAfterGradingInstructionUpdate);

        return updateModelingExercise(updateModelingExerciseDTO, null);
    }

    /**
     * Validate the modeling exercise title.
     * 1. Check presence and length of exercise title
     * 2. Find forbidden patterns in exercise title
     *
     * @param modelingExercise Modeling exercise to be validated
     */
    public void validateTitle(ModelingExercise modelingExercise) {
        // Check if exercise title is set
        if (modelingExercise.getTitle() == null || modelingExercise.getTitle().isBlank() || modelingExercise.getTitle().length() < 3) {
            throw new BadRequestAlertException("The title is not set or is too short.", ENTITY_NAME, "titleLengthInvalid");
        }
        // Check if the exercise title matches regex
        Matcher titleMatcher = TITLE_NAME_PATTERN.matcher(modelingExercise.getTitle());
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
    private void updateGradingCriteria(UpdateModelingExerciseDTO dto, ModelingExercise exercise) {
        if (dto.gradingCriteria() == null || dto.gradingCriteria().isEmpty()) {
            clearInitializedCollection(exercise.getGradingCriteria());
            return;
        }

        Set<GradingCriterion> managedCriteria = ensureGradingCriteriaSet(exercise);

        Map<Long, GradingCriterion> existingById = managedCriteria.stream().filter(gc -> gc.getId() != null)
                .collect(Collectors.toMap(GradingCriterion::getId, gc -> gc, (a, _) -> a));

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
    private Set<GradingCriterion> ensureGradingCriteriaSet(ModelingExercise exercise) {
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
    private void updateCompetencyLinks(UpdateModelingExerciseDTO dto, ModelingExercise exercise) {
        if (dto.competencyLinks() == null || dto.competencyLinks().isEmpty()) {
            clearInitializedCollection(exercise.getCompetencyLinks());
            return;
        }

        Set<CompetencyExerciseLink> managedLinks = ensureCompetencyLinksSet(exercise);

        Map<Long, CompetencyExerciseLink> existingByCompetencyId = managedLinks.stream().filter(link -> link.getCompetency() != null && link.getCompetency().getId() != null)
                .collect(Collectors.toMap(link -> link.getCompetency().getId(), link -> link, (a, _) -> a));

        Long exerciseCourseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;

        Set<CompetencyExerciseLink> updated = new HashSet<>();
        for (var linkDto : dto.competencyLinks()) {

            if (exerciseCourseId != null && linkDto.courseId() != null && !Objects.equals(exerciseCourseId, linkDto.courseId())) {
                throw new BadRequestAlertException("The competency does not belong to the exercise's course.", ENTITY_NAME, "wrongCourse");
            }

            var competencyDto = linkDto.courseCompetencyDTO();
            Long competencyId = competencyDto.id();

            CompetencyExerciseLink link = existingByCompetencyId.get(competencyId);
            if (link == null) {
                Competency competencyRef = competencyRepository.getReferenceById(competencyId);
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
    private Set<CompetencyExerciseLink> ensureCompetencyLinksSet(ModelingExercise exercise) {
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
     * @param exerciseCourseId the course id of the exercise (may be {@code null})
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
            throw new BadRequestAlertException("The competency does not belong to the exercise's course.", ENTITY_NAME, "wrongCourse");
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
     * Applies new updateModelingExerciseDTO's data to the given exercise, mutating it in place.
     * <p>
     * This method follows PUT semantics:
     * <ul>
     * <li>All fields in the DTO represent the new state.</li>
     * <li>Required attributes (e.g. title) are validated here and must not be {@code null} or blank.</li>
     * <li>Nullable attributes are explicitly overwritten, i.e. {@code null} means "clear existing value".</li>
     * <li>Collections (grading criteria, competency links) are fully replaced; {@code null} or empty means "remove all".</li>
     * </ul>
     *
     * @param updateModelingExerciseDTO the DTO containing the updated state for the exercise
     * @param exercise                  the exercise to update (will be mutated)
     * @return the same {@link ModelingExercise} instance after applying the updates
     * @throws BadRequestAlertException if required fields are missing/invalid or a competency from the DTO
     *                                      does not belong to the exercise's course or otherwise violates domain constraints
     */
    public ModelingExercise update(UpdateModelingExerciseDTO updateModelingExerciseDTO, ModelingExercise exercise) {
        if (updateModelingExerciseDTO == null) {
            throw new BadRequestAlertException("No modeling exercise was provided.", ENTITY_NAME, "isNull");
        }

        exercise.setTitle(updateModelingExerciseDTO.title());
        validateTitle(exercise);
        exercise.setShortName(updateModelingExerciseDTO.shortName());
        // problemStatement: null → empty string
        String newProblemStatement = updateModelingExerciseDTO.problemStatement() == null ? "" : updateModelingExerciseDTO.problemStatement();
        exercise.setProblemStatement(newProblemStatement);

        exercise.setChannelName(updateModelingExerciseDTO.channelName());
        exercise.setCategories(updateModelingExerciseDTO.categories());
        exercise.setDifficulty(updateModelingExerciseDTO.difficulty());

        exercise.setMaxPoints(updateModelingExerciseDTO.maxPoints());
        exercise.setBonusPoints(updateModelingExerciseDTO.bonusPoints());
        exercise.setIncludedInOverallScore(updateModelingExerciseDTO.includedInOverallScore());

        exercise.setReleaseDate(updateModelingExerciseDTO.releaseDate());
        exercise.setStartDate(updateModelingExerciseDTO.startDate());
        exercise.setDueDate(updateModelingExerciseDTO.dueDate());
        exercise.setAssessmentDueDate(updateModelingExerciseDTO.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(updateModelingExerciseDTO.exampleSolutionPublicationDate());

        // validates general settings: points, dates
        exercise.validateGeneralSettings();

        exercise.setAllowComplaintsForAutomaticAssessments(updateModelingExerciseDTO.allowComplaintsForAutomaticAssessments());
        exercise.setAllowFeedbackRequests(updateModelingExerciseDTO.allowFeedbackRequests());
        exercise.setPresentationScoreEnabled(updateModelingExerciseDTO.presentationScoreEnabled());
        exercise.setSecondCorrectionEnabled(updateModelingExerciseDTO.secondCorrectionEnabled());
        exercise.setFeedbackSuggestionModule(updateModelingExerciseDTO.feedbackSuggestionModule());
        exercise.setGradingInstructions(updateModelingExerciseDTO.gradingInstructions());

        exercise.setExampleSolutionModel(updateModelingExerciseDTO.exampleSolutionModel());
        exercise.setExampleSolutionExplanation(updateModelingExerciseDTO.exampleSolutionExplanation());

        updateGradingCriteria(updateModelingExerciseDTO, exercise);
        updateCompetencyLinks(updateModelingExerciseDTO, exercise);

        return exercise;
    }
}
