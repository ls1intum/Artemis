package de.tum.cit.aet.artemis.modeling.web;

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
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
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
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.exam.api.ExerciseGroupApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionExportService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ImportModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseListItemDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingExerciseResponseDTO;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseImportService;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseService;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;

/**
 * REST controller for managing ModelingExercise.
 */
@Conditional(ModelingEnabled.class)
@Lazy
@FeatureUsage("authoring/exercise-management")
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

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<SlideApi> slideApi;

    private final Optional<AtlasMLApi> atlasMLApi;

    private final Optional<CompetencyApi> competencyApi;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    private final Optional<ExerciseGroupApi> exerciseGroupApi;

    private final ExerciseVariantGroupService exerciseVariantGroupService;

    public ModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository, UserRepository userRepository, CourseService courseService,
            AuthorizationCheckService authCheckService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ModelingExerciseService modelingExerciseService, ExerciseDeletionService exerciseDeletionService, ModelingExerciseImportService modelingExerciseImportService,
            SubmissionExportService modelingSubmissionExportService, ExerciseService exerciseService, GroupNotificationScheduleService groupNotificationScheduleService,
            GradingCriterionRepository gradingCriterionRepository, ChannelService channelService, ChannelRepository channelRepository,
            ExerciseVersionService exerciseVersionService, Optional<CompetencyProgressApi> competencyProgressApi, Optional<SlideApi> slideApi, Optional<AtlasMLApi> atlasMLApi,
            Optional<CompetencyApi> competencyApi, CompetencyExerciseLinkService competencyExerciseLinkService, Optional<ExerciseGroupApi> exerciseGroupApi,
            ExerciseVariantGroupService exerciseVariantGroupService) {
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
        this.competencyApi = competencyApi;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.exerciseVersionService = exerciseVersionService;
        this.competencyProgressApi = competencyProgressApi;
        this.slideApi = slideApi;
        this.atlasMLApi = atlasMLApi;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
        this.exerciseGroupApi = exerciseGroupApi;
        this.exerciseVariantGroupService = exerciseVariantGroupService;
    }

    // TODO: most of these calls should be done in the context of a course

    /**
     * POST modeling-exercises : Create a new modelingExercise.
     *
     * @param createModelingExerciseDTO the modelingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new
     *         modelingExercise, or with status 400 (Bad Request) if the
     *         modelingExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    // TODO: we should add courses/{courseId} here
    @PostMapping("modeling-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ModelingExerciseResponseDTO> createModelingExercise(@RequestBody UpdateModelingExerciseDTO createModelingExerciseDTO) throws URISyntaxException {
        log.debug("REST request to save ModelingExercise : {}", createModelingExerciseDTO.id());
        if (createModelingExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new modeling exercise cannot already have an ID", ENTITY_NAME, "idExists");
        }
        ModelingExercise modelingExercise = new ModelingExercise();
        applyDtoToNewExercise(createModelingExerciseDTO, modelingExercise);

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
        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(modelingExercise, ENTITY_NAME);

        var competencyLinks = competencyExerciseLinkService.extractCompetencyLinksForCreation(modelingExercise);
        ModelingExercise savedExercise = modelingExerciseRepository.save(modelingExercise);
        if (!competencyLinks.isEmpty()) {
            competencyExerciseLinkService.addCompetencyLinksForCreation(savedExercise, competencyLinks);
            savedExercise = modelingExerciseRepository.save(savedExercise);
        }
        // A client may omit the plagiarism detection config; fill and persist the default for course exercises so it is
        // not stored as null. Done after the competency-link save so it operates on the fully persisted exercise.
        PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(savedExercise, modelingExerciseRepository);
        final ModelingExercise result = savedExercise;

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

        // Guarantee exam.course is initialized before mapping: a second save() above (competency links) would
        // otherwise merge result into a detached instance whose exerciseGroup can come back an uninitialized proxy.
        ensureExamCourseInitialized(result);
        return ResponseEntity.created(new URI("/api/modeling/modeling-exercises/" + result.getId())).body(ModelingExerciseResponseDTO.of(result));
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
    public ResponseEntity<SearchResultPageDTO<ModelingExerciseListItemDTO>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithAuthorities();
        final SearchResultPageDTO<ModelingExercise> page = modelingExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user);
        final List<ModelingExerciseListItemDTO> resultsOnPage = page.getResultsOnPage().stream().map(ModelingExerciseListItemDTO::of).toList();
        return ResponseEntity.ok(new SearchResultPageDTO<>(resultsOnPage, page.getNumberOfPages()));
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
    public ResponseEntity<ModelingExerciseResponseDTO> updateModelingExercise(@RequestBody UpdateModelingExerciseDTO updateModelingExerciseDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ModelingExercise : {}", updateModelingExerciseDTO.id());

        final ModelingExercise originalExercise = modelingExerciseRepository
                .findByIdWithExampleSubmissionsResultsCompetenciesAndGradingCriteriaElseThrow(updateModelingExerciseDTO.id());

        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithAuthorities();
        // Important: use the original exercise for permission check
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalExercise, user);
        // Forbid changing the course the exercise belongs to.
        if (updateModelingExerciseDTO.courseId() == null && updateModelingExerciseDTO.exerciseGroupId() == null) {
            throw new BadRequestAlertException("Either courseId or exerciseGroupId is required.", ENTITY_NAME, "courseOrExerciseGroupMissing");
        }
        // For course exercises, verify the courseId matches; for exam exercises, courseId is null (exerciseGroupId is used instead)
        if (updateModelingExerciseDTO.courseId() != null
                && !Objects.equals(originalExercise.getCourseViaExerciseGroupOrCourseMember().getId(), updateModelingExerciseDTO.courseId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "forbidChangeCourseId");
        }
        // The exercise group itself cannot be changed here — reassignment goes through
        // ExerciseGroupResource#moveExerciseToGroup, which enforces the student-exam safety check (moving an exercise
        // after student exams were generated would desync their selections).
        if (updateModelingExerciseDTO.exerciseGroupId() != null && originalExercise.getExerciseGroup() != null
                && !Objects.equals(originalExercise.getExerciseGroup().getId(), updateModelingExerciseDTO.exerciseGroupId())) {
            throw new ConflictException("The exercise group cannot be changed here.", ENTITY_NAME, "exerciseGroupCannotChange");
        }

        ZonedDateTime oldDueDate = originalExercise.getDueDate();
        ZonedDateTime oldAssessmentDueDate = originalExercise.getAssessmentDueDate();
        ZonedDateTime oldReleaseDate = originalExercise.getReleaseDate();
        Double oldMaxPoints = originalExercise.getMaxPoints();
        Double oldBonusPoints = originalExercise.getBonusPoints();
        String oldProblemStatement = originalExercise.getProblemStatement();
        // Capture original competency IDs before update() mutates the entity (L1 cache)
        Set<Long> originalCompetencyIds = originalExercise.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

        // whether is exam exercise or course exercise are not changeable
        ModelingExercise updatedExercise = update(updateModelingExerciseDTO, originalExercise);
        // Valid exercises have set either a course or an exerciseGroup
        updatedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(updatedExercise, originalExercise, ENTITY_NAME);

        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(updatedExercise, ENTITY_NAME);

        channelService.updateExerciseChannel(originalExercise, updatedExercise);

        ModelingExercise persistedExercise = modelingExerciseRepository.save(updatedExercise);

        exerciseService.logUpdate(updatedExercise, updatedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(oldMaxPoints, oldBonusPoints, persistedExercise);

        participationRepository.removeIndividualDueDatesIfBeforeDueDate(persistedExercise, oldDueDate);
        exerciseService.checkExampleSubmissions(persistedExercise);

        exerciseService.notifyAboutExerciseChanges(oldReleaseDate, oldAssessmentDueDate, oldProblemStatement, persistedExercise, notificationText);
        slideApi.ifPresent(api -> api.handleDueDateChange(oldDueDate, persistedExercise));

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, persistedExercise));

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

        // Guarantee exam.course is initialized before mapping: save() merges the detached originalExercise, and merge
        // can resolve the non-cascaded exerciseGroup association to an uninitialized proxy.
        ensureExamCourseInitialized(persistedExercise);
        return ResponseEntity.ok(ModelingExerciseResponseDTO.of(persistedExercise));
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
    public ResponseEntity<List<ModelingExerciseListItemDTO>> getModelingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ModelingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<ModelingExercise> exercises = modelingExerciseRepository.findByCourseIdWithCategories(courseId);
        List<ModelingExerciseListItemDTO> result = exercises.stream().map(ModelingExerciseListItemDTO::of).toList();
        return ResponseEntity.ok().body(result);
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
    public ResponseEntity<ModelingExerciseResponseDTO> getModelingExercise(@PathVariable Long exerciseId) {
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

        // The edit form locks its timeline pickers when the exercise belongs to a variant group, so the response has to
        // carry the group. Resolved by exercise id rather than fetch-joined: a sixth path on the entity graph above
        // would cross the query-quality over-fetch threshold.
        exerciseVariantGroupService.findOwningGroup(exerciseId).ifPresent(modelingExercise::setExerciseVariantGroup);

        // Guarantee exam.course is initialized before mapping, deterministically rather than relying on the access
        // check above happening to touch it.
        ensureExamCourseInitialized(modelingExercise);
        return ResponseEntity.ok().body(ModelingExerciseResponseDTO.of(modelingExercise));
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

        User user = userRepository.getUserWithAuthorities();
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
     * @param sourceExerciseId  The ID of the original exercise which should get
     *                              imported
     * @param importExerciseDTO The new exercise containing values that should get
     *                              overwritten in the imported exercise, s.a. the title
     *                              or difficulty
     * @return The imported exercise (200), a not found error (404) if the template
     *         does not exist, or a forbidden error
     *         (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("modeling-exercises/import")
    @EnforceAtLeastEditor
    public ResponseEntity<ModelingExerciseResponseDTO> importExercise(@RequestParam long sourceExerciseId, @RequestBody ImportModelingExerciseDTO importExerciseDTO)
            throws URISyntaxException {
        // Build a transient entity from the dumb DTO, attaching managed Course/ExerciseGroup loaded by id.
        final ModelingExercise importedExercise = toExercise(importExerciseDTO);
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        final var user = userRepository.getUserWithAuthorities();
        final var originalModelingExercise = modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalModelingExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();
        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(importedExercise, ENTITY_NAME);

        final var newModelingExercise = modelingExerciseImportService.importModelingExercise(importedExercise, originalModelingExercise);
        modelingExerciseRepository.save(newModelingExercise);
        // Notify AtlasML about the imported exercise
        atlasMLApi.ifPresent(api -> api.saveExerciseWithCompetencies(newModelingExercise));
        exerciseVersionService.createExerciseVersion(newModelingExercise, user);

        // Guarantee exam.course is initialized before mapping: the import service's second save() (competency links)
        // merges a detached instance whose exerciseGroup can come back an uninitialized proxy.
        ensureExamCourseInitialized(newModelingExercise);
        return ResponseEntity.created(new URI("/api/modeling/modeling-exercises/" + newModelingExercise.getId())).body(ModelingExerciseResponseDTO.of(newModelingExercise));
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
        if (submissionExportOptions.exportAllParticipants()) {
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
    public ResponseEntity<ModelingExerciseResponseDTO> reEvaluateAndUpdateModelingExercise(@PathVariable long exerciseId,
            @RequestBody UpdateModelingExerciseDTO updateModelingExerciseDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate ModelingExercise : {}", updateModelingExerciseDTO.id());

        final ModelingExercise existingExercise = modelingExerciseRepository.findByIdWithExampleSubmissionsResultsCompetenciesAndGradingCriteriaElseThrow(exerciseId);
        authCheckService.checkGivenExerciseIdSameForExerciseRequestBodyIdElseThrow(exerciseId, updateModelingExerciseDTO.id());

        // Capture ALL original values BEFORE update() mutates the entity via L1 cache.
        final Double originalMaxPoints = existingExercise.getMaxPoints();
        final Double originalBonusPoints = existingExercise.getBonusPoints();
        final ZonedDateTime originalDueDate = existingExercise.getDueDate();
        final ZonedDateTime originalReleaseDate = existingExercise.getReleaseDate();
        final ZonedDateTime originalAssessmentDueDate = existingExercise.getAssessmentDueDate();
        final String originalProblemStatement = existingExercise.getProblemStatement();
        final Set<Long> originalCompetencyIds = Hibernate.isInitialized(existingExercise.getCompetencyLinks())
                ? existingExercise.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet())
                : Set.of();

        var user = userRepository.getUserWithAuthorities();
        // Apply DTO changes BEFORE re-evaluation so that updated grading criteria take effect.
        ModelingExercise exerciseForReevaluation = update(updateModelingExerciseDTO, existingExercise);
        var course = courseRepository.findByIdElseThrow(exerciseForReevaluation.getCourseViaExerciseGroupOrCourseMember().getId());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(exerciseForReevaluation, ENTITY_NAME);

        exerciseService.reEvaluateExercise(exerciseForReevaluation, deleteFeedbackAfterGradingInstructionUpdate);

        // Save directly instead of delegating to updateModelingExercise() to avoid double side effects.
        ModelingExercise savedExercise = modelingExerciseRepository.save(exerciseForReevaluation);

        // Apply all post-save side effects once with the captured originals.
        exerciseService.logUpdate(savedExercise, savedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(originalMaxPoints, originalBonusPoints, savedExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedExercise, originalDueDate);
        exerciseService.checkExampleSubmissions(savedExercise);
        exerciseService.notifyAboutExerciseChanges(originalReleaseDate, originalAssessmentDueDate, originalProblemStatement, savedExercise, null);
        slideApi.ifPresent(api -> api.handleDueDateChange(originalDueDate, savedExercise));
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, savedExercise));
        exerciseVersionService.createExerciseVersion(savedExercise);

        // Guarantee exam.course is initialized before mapping: save() merges the detached exercise, and merge can
        // resolve the non-cascaded exerciseGroup association to an uninitialized proxy.
        ensureExamCourseInitialized(savedExercise);
        return ResponseEntity.ok(ModelingExerciseResponseDTO.of(savedExercise));
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

        Set<GradingCriterion> managedCriteria = exercise.ensureGradingCriteriaSet();

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
    private ModelingExercise update(UpdateModelingExerciseDTO updateModelingExerciseDTO, ModelingExercise exercise) {
        if (updateModelingExerciseDTO == null) {
            throw new BadRequestAlertException("No modeling exercise was provided.", ENTITY_NAME, "isNull");
        }
        exercise.setTitle(updateModelingExerciseDTO.title());
        exercise.validateTitle();
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

        // A variant group owns its members' timeline, so pin the dates back to the group before validating.
        exerciseVariantGroupService.applyOwningGroupTimeline(exercise);

        // validates general settings: points, dates, etc.
        exercise.validateGeneralSettings();

        // Only set boolean values if they are explicitly provided (not null)
        // This allows partial updates without requiring all boolean fields
        if (updateModelingExerciseDTO.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(updateModelingExerciseDTO.allowComplaintsForAutomaticAssessments());
        }
        if (updateModelingExerciseDTO.presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(updateModelingExerciseDTO.presentationScoreEnabled());
        }
        if (updateModelingExerciseDTO.secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(updateModelingExerciseDTO.secondCorrectionEnabled());
        }
        exercise.setGradingInstructions(updateModelingExerciseDTO.gradingInstructions());
        if (updateModelingExerciseDTO.plagiarismDetectionConfig() != null) {
            PlagiarismDetectionConfig config = toPlagiarismDetectionConfig(updateModelingExerciseDTO.plagiarismDetectionConfig());
            PlagiarismDetectionConfig existingConfig = exercise.getPlagiarismDetectionConfig();
            if (existingConfig != null) {
                // Reuse the exercise's own row id (never a client-sent one) so Hibernate merges the existing row
                // instead of orphan-deleting it and inserting a new one: Exercise.plagiarismDetectionConfig is a
                // @OneToOne(cascade = ALL, orphanRemoval = true) association.
                config.setId(existingConfig.getId());
            }
            exercise.setPlagiarismDetectionConfig(config);
        }

        // The diagram type is immutable after creation because changing it would invalidate existing submissions.
        exercise.setExampleSolutionModel(updateModelingExerciseDTO.exampleSolutionModel());
        exercise.setExampleSolutionExplanation(updateModelingExerciseDTO.exampleSolutionExplanation());

        updateGradingCriteria(updateModelingExerciseDTO, exercise);
        competencyExerciseLinkService.updateCompetencyLinks(updateModelingExerciseDTO, exercise);

        return exercise;
    }

    /**
     * Applies DTO values to a new {@link ModelingExercise} entity for creation via POST.
     * Sets courseId/exerciseGroupId as proxy objects so that
     * {@link CourseService#retrieveCourseOverExerciseGroupOrCourseId} can resolve them.
     *
     * @param dto      the create DTO
     * @param exercise the new transient exercise to populate
     */
    private void applyDtoToNewExercise(UpdateModelingExerciseDTO dto, ModelingExercise exercise) {
        exercise.setTitle(dto.title());
        exercise.setShortName(dto.shortName());
        exercise.setProblemStatement(dto.problemStatement());
        exercise.setChannelName(dto.channelName());
        exercise.setCategories(dto.categories());
        exercise.setDifficulty(dto.difficulty());
        exercise.setMaxPoints(dto.maxPoints());
        exercise.setBonusPoints(dto.bonusPoints());
        // Keep the entity default when an older or third-party client omits this field.
        if (dto.includedInOverallScore() != null) {
            exercise.setIncludedInOverallScore(dto.includedInOverallScore());
        }
        exercise.setReleaseDate(dto.releaseDate());
        exercise.setStartDate(dto.startDate());
        exercise.setDueDate(dto.dueDate());
        exercise.setAssessmentDueDate(dto.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(dto.exampleSolutionPublicationDate());
        exercise.setGradingInstructions(dto.gradingInstructions());
        exercise.setDiagramType(dto.diagramType());
        exercise.setExampleSolutionModel(dto.exampleSolutionModel());
        exercise.setExampleSolutionExplanation(dto.exampleSolutionExplanation());
        // The create DTO does not carry the assessment type; modeling exercises were always created as MANUAL (the client
        // model default). Set it explicitly so a new exercise is not persisted with a null assessment type.
        exercise.setAssessmentType(AssessmentType.MANUAL);
        // Mode and team configuration are only set at creation time (immutable afterwards). Guard against null so the
        // entity keeps its INDIVIDUAL default when the (client) DTO omits the mode.
        if (dto.mode() != null) {
            exercise.setMode(dto.mode());
        }
        if (dto.teamAssignmentConfig() != null) {
            exercise.setTeamAssignmentConfig(dto.teamAssignmentConfig().toEntity());
        }
        if (dto.plagiarismDetectionConfig() != null) {
            exercise.setPlagiarismDetectionConfig(toPlagiarismDetectionConfig(dto.plagiarismDetectionConfig()));
        }
        if (dto.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(dto.allowComplaintsForAutomaticAssessments());
        }
        if (dto.presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(dto.presentationScoreEnabled());
        }
        if (dto.secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(dto.secondCorrectionEnabled());
        }

        // Transfer grading criteria from the DTO
        if (dto.gradingCriteria() != null && !dto.gradingCriteria().isEmpty()) {
            for (var gcDto : dto.gradingCriteria()) {
                GradingCriterion criterion = gcDto.toEntity();
                criterion.setExercise(exercise);
                exercise.getGradingCriteria().add(criterion);
            }
        }

        // Transfer competency links from the DTO (extractCompetencyLinksForCreation will handle them)
        if (dto.competencyLinks() != null && !dto.competencyLinks().isEmpty()) {
            for (var linkDto : dto.competencyLinks()) {
                if (linkDto == null || linkDto.competency() == null) {
                    throw new BadRequestAlertException("Each competency link must include a competency.", ENTITY_NAME, "competencyIdMissing");
                }
                Competency competencyRef = new Competency();
                competencyRef.setId(linkDto.competency().id());
                CompetencyExerciseLink link = new CompetencyExerciseLink(competencyRef, exercise, linkDto.weight());
                exercise.getCompetencyLinks().add(link);
            }
        }

        // Set course and/or exercise group references from the ids. The exclusivity invariant (exactly one of the two) is
        // validated downstream by checkCourseAndExerciseGroupExclusivity, so a request carrying both is correctly rejected.
        if (dto.courseId() != null) {
            Course courseRef = new Course();
            courseRef.setId(dto.courseId());
            exercise.setCourse(courseRef);
        }
        if (dto.exerciseGroupId() != null) {
            ExerciseGroup exerciseGroup = new ExerciseGroup();
            exerciseGroup.setId(dto.exerciseGroupId());
            exercise.setExerciseGroup(exerciseGroup);
        }
    }

    /**
     * Builds a transient {@link ModelingExercise} from the import request DTO.
     * <p>
     * Only scalar/enum/date fields and nested config are set. The Course / ExerciseGroup referenced by id are loaded as
     * managed entities (so the subsequent role checks have access to the configured groups) and attached to the new
     * transient exercise. No entity graph from the request is persisted.
     *
     * @param dto the import payload
     * @return a transient ModelingExercise carrying the values to overwrite in the imported exercise
     */
    private ModelingExercise toExercise(ImportModelingExerciseDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("No modeling exercise was provided.", ENTITY_NAME, "isNull");
        }
        ModelingExercise exercise = new ModelingExercise();
        exercise.setId(dto.id());
        exercise.setTitle(dto.title());
        exercise.setChannelName(dto.channelName());
        exercise.setShortName(dto.shortName());
        exercise.setProblemStatement(dto.problemStatement());
        exercise.setCategories(dto.categories());
        exercise.setDifficulty(dto.difficulty());
        // mode and includedInOverallScore have non-null entity defaults (INDIVIDUAL, INCLUDED_COMPLETELY) that the old
        // entity request body preserved when a client omitted them. Guard the setters so an import payload that omits
        // them keeps those defaults instead of overwriting with null. Mirrors the create path's mode guard.
        if (dto.mode() != null) {
            exercise.setMode(dto.mode());
        }
        // Modeling exercises are always manually assessed (mirrors the create path); the import DTO does not carry
        // assessmentType, so set it explicitly to avoid copyExerciseBasis persisting null.
        exercise.setAssessmentType(AssessmentType.MANUAL);
        exercise.setMaxPoints(dto.maxPoints());
        exercise.setBonusPoints(dto.bonusPoints());
        if (dto.includedInOverallScore() != null) {
            exercise.setIncludedInOverallScore(dto.includedInOverallScore());
        }
        if (dto.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(dto.allowComplaintsForAutomaticAssessments());
        }
        if (dto.presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(dto.presentationScoreEnabled());
        }
        if (dto.secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(dto.secondCorrectionEnabled());
        }
        exercise.setGradingInstructions(dto.gradingInstructions());
        exercise.setReleaseDate(dto.releaseDate());
        exercise.setStartDate(dto.startDate());
        exercise.setDueDate(dto.dueDate());
        exercise.setAssessmentDueDate(dto.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(dto.exampleSolutionPublicationDate());
        // Modeling-specific fields (copyModelingExerciseBasis copies these from the imported exercise → null otherwise).
        exercise.setDiagramType(dto.diagramType());
        exercise.setExampleSolutionModel(dto.exampleSolutionModel());
        exercise.setExampleSolutionExplanation(dto.exampleSolutionExplanation());

        if (dto.teamAssignmentConfig() != null) {
            exercise.setTeamAssignmentConfig(dto.teamAssignmentConfig().toEntity());
        }
        if (dto.plagiarismDetectionConfig() != null) {
            exercise.setPlagiarismDetectionConfig(toPlagiarismDetectionConfig(dto.plagiarismDetectionConfig()));
        }

        // Grading criteria (with their structured grading instructions, needed for the copy tracker during import)
        if (dto.gradingCriteria() != null && !dto.gradingCriteria().isEmpty()) {
            Set<GradingCriterion> criteria = new HashSet<>();
            dto.gradingCriteria().forEach(gcDto -> {
                GradingCriterion criterion = gcDto.toEntity();
                criterion.setExercise(exercise);
                criteria.add(criterion);
            });
            exercise.setGradingCriteria(criteria);
        }

        // Competency links as new unmanaged objects referencing only the competency id
        if (dto.competencyLinks() != null && !dto.competencyLinks().isEmpty()) {
            Set<CompetencyExerciseLink> links = new HashSet<>();
            for (CompetencyLinkDTO linkDto : dto.competencyLinks()) {
                if (linkDto == null || linkDto.competency() == null) {
                    throw new BadRequestAlertException("Each competency link must include a competency.", ENTITY_NAME, "competencyIdMissing");
                }
                Competency competencyRef = new Competency();
                competencyRef.setId(linkDto.competency().id());
                links.add(new CompetencyExerciseLink(competencyRef, exercise, linkDto.weight()));
            }
            exercise.setCompetencyLinks(links);
        }

        // Attach a managed Course and/or ExerciseGroup so role checks see the configured groups. The exclusivity invariant
        // (exactly one of the two) is validated downstream by checkCourseAndExerciseGroupExclusivity.
        if (dto.courseId() != null) {
            Course course = courseRepository.findByIdElseThrow(dto.courseId());
            exercise.setCourse(course);
        }
        if (dto.exerciseGroupId() != null) {
            ExerciseGroup exerciseGroup = exerciseGroupApi.orElseThrow(() -> new ExamApiNotPresentException(ExerciseGroupApi.class)).findByIdElseThrow(dto.exerciseGroupId());
            exercise.setExerciseGroup(exerciseGroup);
        }
        return exercise;
    }

    /**
     * Ensures {@code exercise.exerciseGroup.exam.course} is initialized before the exercise is mapped to a response
     * DTO. Management screens build the "Exam" link from {@code exerciseGroup.exam.course.id}; a second
     * {@code repository.save()} call on an already-persisted exercise merges a detached instance, and JPA merge
     * resolves non-cascaded to-one associations (such as {@code exerciseGroup}) to a fresh, uninitialized proxy even
     * though the mapping default is eager. Re-fetching the group here, inside its own transactional repository call,
     * deterministically hydrates the chain before the DTO mapper (which runs with no active session) touches it, so
     * this cannot throw a {@code LazyInitializationException}.
     *
     * @param exercise the exercise about to be mapped to a {@link ModelingExerciseResponseDTO}
     */
    private void ensureExamCourseInitialized(ModelingExercise exercise) {
        if (exercise.isExamExercise()) {
            ExerciseGroup exerciseGroup = exerciseGroupApi.orElseThrow(() -> new ExamApiNotPresentException(ExerciseGroupApi.class))
                    .findByIdElseThrow(exercise.getExerciseGroup().getId());
            exercise.setExerciseGroup(exerciseGroup);
        }
    }

    private static PlagiarismDetectionConfig toPlagiarismDetectionConfig(PlagiarismDetectionConfigDTO dto) {
        // Deliberately id-less: create and import must always produce a fresh row, and update re-attaches the
        // exercise's own stored config id at the call site — a client-sent id is never trusted.
        PlagiarismDetectionConfig config = new PlagiarismDetectionConfig();
        config.setContinuousPlagiarismControlEnabled(dto.continuousPlagiarismControlEnabled());
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(dto.continuousPlagiarismControlPostDueDateChecksEnabled());
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(dto.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod());
        config.setSimilarityThreshold(dto.similarityThreshold());
        config.setMinimumScore(dto.minimumScore());
        config.setMinimumSize(dto.minimumSize());
        return config;
    }
}
