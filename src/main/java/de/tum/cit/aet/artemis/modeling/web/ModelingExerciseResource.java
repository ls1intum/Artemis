package de.tum.cit.aet.artemis.modeling.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<SlideApi> slideApi;

    private final Optional<AtlasMLApi> atlasMLApi;

    public ModelingExerciseResource(ModelingExerciseRepository modelingExerciseRepository, UserRepository userRepository, CourseService courseService,
            AuthorizationCheckService authCheckService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ModelingExerciseService modelingExerciseService, ExerciseDeletionService exerciseDeletionService, ModelingExerciseImportService modelingExerciseImportService,
            SubmissionExportService modelingSubmissionExportService, ExerciseService exerciseService, GroupNotificationScheduleService groupNotificationScheduleService,
            GradingCriterionRepository gradingCriterionRepository, ChannelService channelService, ChannelRepository channelRepository,
            Optional<CompetencyProgressApi> competencyProgressApi, Optional<SlideApi> slideApi, Optional<AtlasMLApi> atlasMLApi) {
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
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.slideApi = slideApi;
        this.atlasMLApi = atlasMLApi;
    }

    // TODO: most of these calls should be done in the context of a course

    /**
     * POST modeling-exercises : Create a new modelingExercise.
     *
     * @param modelingExercise the modelingExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new modelingExercise, or with status 400 (Bad Request) if the modelingExercise has already an ID
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

        return ResponseEntity.created(new URI("/api/modeling-exercises/" + result.getId())).body(result);
    }

    /**
     * Search for all modeling exercises by id, title and course title. The result is pageable since there might be hundreds
     * of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
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
    // TODO: change the request body to a DTO to support the new hibernate version 6.6
    // NOTE: IMPORTANT we should NEVER call save on an entity retrieved from the client because it is unsafe and can lead to data loss
    @PutMapping("modeling-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ModelingExercise> updateModelingExercise(@RequestBody UpdateModelingExerciseDTO updateModelingExerciseDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ModelingExercise : {}", updateModelingExerciseDTO);

        final ModelingExercise modelingExerciseBeforeUpdate = modelingExerciseRepository
                .findWithEagerExampleSubmissionsAndCompetenciesByIdElseThrow(updateModelingExerciseDTO.id());
        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Important: use the original exercise for permission check
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, modelingExerciseBeforeUpdate, user);

        Long currentCourseId = modelingExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId();
        if (updateModelingExerciseDTO.courseId() != null && !Objects.equals(currentCourseId, updateModelingExerciseDTO.courseId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        // whether is exam exercise or course exercise are not changeable
        ModelingExercise modelingExercise = updateModelingExerciseDTO.update(modelingExerciseBeforeUpdate);

        // validates general settings: points, dates
        modelingExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        modelingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(modelingExercise, modelingExerciseBeforeUpdate, ENTITY_NAME);

        channelService.updateExerciseChannel(modelingExerciseBeforeUpdate, modelingExercise);

        ModelingExercise updatedModelingExercise = exerciseService.saveWithCompetencyLinks(modelingExercise, modelingExerciseRepository::save);

        exerciseService.logUpdate(modelingExercise, modelingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(modelingExerciseBeforeUpdate, updatedModelingExercise);

        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedModelingExercise, modelingExerciseBeforeUpdate.getDueDate());
        exerciseService.checkExampleSubmissions(updatedModelingExercise);

        exerciseService.notifyAboutExerciseChanges(modelingExerciseBeforeUpdate, updatedModelingExercise, notificationText);
        slideApi.ifPresent(api -> api.handleDueDateChange(modelingExerciseBeforeUpdate, updatedModelingExercise));

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(modelingExerciseBeforeUpdate, Optional.of(updatedModelingExercise)));

        // Notify AtlasML about the modeling exercise update
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(updatedModelingExercise, OperationTypeDTO.UPDATE);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about modeling exercise update: {}", e.getMessage());
            }
        });

        return ResponseEntity.ok(updatedModelingExercise);
    }

    /**
     * GET /courses/:courseId/modeling-exercises : get all the exercises.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of modelingExercises in body
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
     * @return the ResponseEntity with status 200 (OK) and with body the modelingExercise, or with status 404 (Not Found)
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
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(modelingExercise, modelingExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, modelingExercise.getTitle())).build();
    }

    /**
     * POST modeling-exercises/import: Imports an existing modeling exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and Dates.
     * Referenced entities will get cloned and assigned a new id.
     * Uses {@link ModelingExerciseImportService}.
     *
     * @param sourceExerciseId The ID of the original exercise which should get imported
     * @param importedExercise The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist, or a forbidden error
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

        return ResponseEntity.created(new URI("/api/modeling-exercises/" + newModelingExercise.getId())).body(newModelingExercise);
    }

    /**
     * POST modeling-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
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
     * PUT modeling-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an existing modelingExercise.
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

        // Get the existing exercise
        final ModelingExercise existingExercise = modelingExerciseRepository.findByIdWithStudentParticipationsSubmissionsResultsElseThrow(exerciseId);
        // Check that the exercise ID in path matches the DTO ID
        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyIdElseThrow(exerciseId, updateModelingExerciseDTO.id());

        var user = userRepository.getUserWithGroupsAndAuthorities();
        // make sure the course actually exists
        ModelingExercise exerciseForReevaluation = updateModelingExerciseDTO.update(existingExercise);
        var course = courseRepository.findByIdElseThrow(exerciseForReevaluation.getCourseViaExerciseGroupOrCourseMember().getId());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(exerciseForReevaluation, deleteFeedbackAfterGradingInstructionUpdate);

        return updateModelingExercise(updateModelingExerciseDTO, null);
    }
}
