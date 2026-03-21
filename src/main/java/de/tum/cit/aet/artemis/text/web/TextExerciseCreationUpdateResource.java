package de.tum.cit.aet.artemis.text.web;

import static de.tum.cit.aet.artemis.core.util.DTOHelper.setIfPresent;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.DTOHelper;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

/**
 * REST controller for creating and updating text exercises.
 */
@Conditional(TextEnabled.class)
@Lazy
@RestController
@RequestMapping("api/text/")
public class TextExerciseCreationUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseCreationUpdateResource.class);

    private static final String ENTITY_NAME = "textExercise";

    private final ExerciseService exerciseService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ChannelService channelService;

    private final Optional<AthenaApi> athenaApi;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<CompetencyApi> competencyApi;

    private final Optional<SlideApi> slideApi;

    private final Optional<AtlasMLApi> atlasMLApi;

    private final TextExerciseRepository textExerciseRepository;

    private final UserRepository userRepository;

    private final ParticipationRepository participationRepository;

    private final ExerciseVersionService exerciseVersionService;

    public TextExerciseCreationUpdateResource(TextExerciseRepository textExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, ParticipationRepository participationRepository, ExerciseService exerciseService,
            GroupNotificationScheduleService groupNotificationScheduleService, InstanceMessageSendService instanceMessageSendService, ChannelService channelService,
            ExerciseVersionService exerciseVersionService, Optional<AthenaApi> athenaApi, Optional<CompetencyProgressApi> competencyProgressApi,
            Optional<CompetencyApi> competencyApi, Optional<SlideApi> slideApi, Optional<AtlasMLApi> atlasMLApi) {
        this.textExerciseRepository = textExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationRepository = participationRepository;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.exerciseService = exerciseService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.channelService = channelService;
        this.exerciseVersionService = exerciseVersionService;
        this.athenaApi = athenaApi;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyApi = competencyApi;
        this.slideApi = slideApi;
        this.atlasMLApi = atlasMLApi;
    }

    /**
     * POST /text-exercises : Create a new textExercise.
     *
     * @param textExercise the textExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new
     *         textExercise, or
     *         with status 400 (Bad Request) if the textExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> createTextExercise(@RequestBody TextExercise textExercise) throws URISyntaxException {
        log.debug("REST request to save TextExercise : {}", textExercise);
        if (textExercise.getId() != null) {
            throw new BadRequestAlertException("A new textExercise cannot already have an ID", ENTITY_NAME, "idExists");
        }

        if (textExercise.getTitle() == null) {
            throw new BadRequestAlertException("A new textExercise needs a title", ENTITY_NAME, "missingtitle");
        }
        // validates general settings: points, dates
        textExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        textExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExercise);
        // Check that the user is authorized to create the exercise
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(textExercise, ENTITY_NAME);

        // Check that only allowed athena modules are used
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(textExercise, course, ENTITY_NAME), () -> textExercise.setFeedbackSuggestionModule(null));

        var competencyLinks = exerciseService.extractCompetencyLinksForCreation(textExercise);
        TextExercise savedExercise = textExerciseRepository.save(textExercise);
        if (!competencyLinks.isEmpty()) {
            exerciseService.addCompetencyLinksForCreation(savedExercise, competencyLinks);
            savedExercise = textExerciseRepository.save(savedExercise);
        }
        final TextExercise result = savedExercise;

        channelService.createExerciseChannel(result, Optional.ofNullable(textExercise.getChannelName()));
        instanceMessageSendService.sendTextExerciseSchedule(result.getId());
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(textExercise);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(result));

        // Notify AtlasML about the new text exercise
        notifyAtlasML(result, OperationTypeDTO.UPDATE, "text exercise creation");

        exerciseVersionService.createExerciseVersion(result);

        return ResponseEntity.created(new URI("/api/text/text-exercises/" + result.getId())).body(result);
    }

    /**
     * PUT /text-exercises : Updates an existing textExercise.
     *
     * @param updateTextExerciseDTO the textExercise DTO to update
     * @param notificationText      about the text exercise update that should be
     *                                  displayed for the student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated
     *         textExercise, or with status 400 (Bad Request) if the textExercise is not valid, or
     *         with status 500 (Internal Server Error) if the textExercise couldn't be updated
     */
    @PutMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> updateTextExercise(@RequestBody UpdateTextExerciseDTO updateTextExerciseDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update TextExercise : {}", updateTextExerciseDTO);

        // If no id is provided, delegate to create
        if (updateTextExerciseDTO.id() == null) {
            TextExercise textExercise = new TextExercise();
            applyDtoToNewExercise(updateTextExerciseDTO, textExercise);
            try {
                return createTextExercise(textExercise);
            }
            catch (URISyntaxException e) {
                throw new BadRequestAlertException("Invalid URI syntax", ENTITY_NAME, "uriSyntaxError");
            }
        }

        final TextExercise originalExercise = textExerciseRepository.findWithCompetenciesCategoriesAndGradingCriteriaByIdElseThrow(updateTextExerciseDTO.id());

        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Important: use the original exercise for permission check
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalExercise, user);

        // Forbid changing the course the exercise belongs to.
        if (updateTextExerciseDTO.courseId() == null && updateTextExerciseDTO.exerciseGroupId() == null) {
            throw new BadRequestAlertException("Either courseId or exerciseGroupId must be provided.", ENTITY_NAME, "courseOrExerciseGroupMissing");
        }
        if (!Objects.equals(originalExercise.getCourseViaExerciseGroupOrCourseMember().getId(), updateTextExerciseDTO.courseId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        ZonedDateTime oldDueDate = originalExercise.getDueDate();
        ZonedDateTime oldAssessmentDueDate = originalExercise.getAssessmentDueDate();
        ZonedDateTime oldReleaseDate = originalExercise.getReleaseDate();
        Double oldMaxPoints = originalExercise.getMaxPoints();
        Double oldBonusPoints = originalExercise.getBonusPoints();
        String oldProblemStatement = originalExercise.getProblemStatement();
        String oldFeedbackSuggestionModule = originalExercise.getFeedbackSuggestionModule();

        // Apply the DTO to the original exercise
        TextExercise updatedExercise = update(updateTextExerciseDTO, originalExercise);
        // Valid exercises have set either a course or an exerciseGroup
        updatedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(updatedExercise, originalExercise, ENTITY_NAME);

        // Validate plagiarism detection config
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(updatedExercise, ENTITY_NAME);

        // Check that only allowed athena modules are used
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(originalExercise);
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(updatedExercise, course, ENTITY_NAME), () -> updatedExercise.setFeedbackSuggestionModule(null));
        // Changing Athena module after the due date has passed is not allowed
        // Use a proxy exercise with the old module for comparison since update() mutates the original
        TextExercise exerciseWithOldModule = new TextExercise();
        exerciseWithOldModule.setFeedbackSuggestionModule(oldFeedbackSuggestionModule);
        exerciseWithOldModule.setDueDate(oldDueDate);
        athenaApi.ifPresent(api -> api.checkValidAthenaModuleChange(exerciseWithOldModule, updatedExercise, ENTITY_NAME));

        channelService.updateExerciseChannel(originalExercise, updatedExercise);

        TextExercise persistedExercise = textExerciseRepository.save(updatedExercise);

        exerciseService.logUpdate(persistedExercise, persistedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(oldMaxPoints, oldBonusPoints, persistedExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(persistedExercise, oldDueDate);
        instanceMessageSendService.sendTextExerciseSchedule(persistedExercise.getId());
        exerciseService.checkExampleSubmissions(persistedExercise);
        exerciseService.notifyAboutExerciseChanges(oldReleaseDate, oldAssessmentDueDate, oldProblemStatement, persistedExercise, notificationText);
        slideApi.ifPresent(api -> api.handleDueDateChange(oldDueDate, persistedExercise));

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(originalExercise, Optional.of(persistedExercise)));

        // Notify AtlasML about the text exercise update
        notifyAtlasML(persistedExercise, OperationTypeDTO.UPDATE, "text exercise update");

        exerciseVersionService.createExerciseVersion(persistedExercise);

        return ResponseEntity.ok(persistedExercise);
    }

    /**
     * PUT /text-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an
     * existing textExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param updateTextExerciseDTO                       the textExercise DTO to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that
     *                                                        indicates whether the
     *                                                        associated feedback should
     *                                                        be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated
     *         textExercise, or with status 400 (Bad Request) if the textExercise is not valid, or
     *         with status 409 (Conflict) if given exerciseId is not same as in the object of the request body, or
     *         with status 500 (Internal Server Error) if the textExercise couldn't be updated
     */
    @PutMapping("text-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> reEvaluateAndUpdateTextExercise(@PathVariable long exerciseId, @RequestBody UpdateTextExerciseDTO updateTextExerciseDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate TextExercise : {}", updateTextExerciseDTO);

        final TextExercise existingExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(exerciseId);
        authCheckService.checkGivenExerciseIdSameForExerciseRequestBodyIdElseThrow(exerciseId, updateTextExerciseDTO.id());

        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Apply DTO to existing exercise
        TextExercise exerciseForReevaluation = update(updateTextExerciseDTO, existingExercise);
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(exerciseForReevaluation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(exerciseForReevaluation, deleteFeedbackAfterGradingInstructionUpdate);

        return updateTextExercise(updateTextExerciseDTO, null);
    }

    /**
     * Applies the DTO's data to the given exercise, mutating it in place.
     *
     * @param dto      the DTO containing the updated state
     * @param exercise the exercise to update (will be mutated)
     * @return the same exercise instance after applying the updates
     */
    private TextExercise update(UpdateTextExerciseDTO dto, TextExercise exercise) {
        if (dto == null) {
            throw new BadRequestAlertException("No text exercise was provided.", ENTITY_NAME, "isNull");
        }
        applyCommonFields(dto, exercise);
        exercise.validateTitle();
        exercise.setProblemStatement(Objects.requireNonNullElse(dto.problemStatement(), ""));

        // validates general settings: points, dates, etc.
        exercise.validateGeneralSettings();

        updateGradingCriteria(dto, exercise);
        updateCompetencyLinks(dto, exercise);

        return exercise;
    }

    /**
     * Applies fields common to both create and update operations from the DTO to the exercise.
     */
    private void applyCommonFields(UpdateTextExerciseDTO dto, TextExercise exercise) {
        exercise.setTitle(dto.title());
        exercise.setShortName(dto.shortName());
        exercise.setChannelName(dto.channelName());
        exercise.setCategories(dto.categories());
        exercise.setDifficulty(dto.difficulty());
        exercise.setMaxPoints(dto.maxPoints());
        exercise.setBonusPoints(dto.bonusPoints());
        exercise.setIncludedInOverallScore(dto.includedInOverallScore());
        exercise.setReleaseDate(dto.releaseDate());
        exercise.setStartDate(dto.startDate());
        exercise.setDueDate(dto.dueDate());
        exercise.setAssessmentDueDate(dto.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(dto.exampleSolutionPublicationDate());
        setIfPresent(dto.allowComplaintsForAutomaticAssessments(), exercise::setAllowComplaintsForAutomaticAssessments);
        setIfPresent(dto.allowFeedbackRequests(), exercise::setAllowFeedbackRequests);
        setIfPresent(dto.presentationScoreEnabled(), exercise::setPresentationScoreEnabled);
        setIfPresent(dto.secondCorrectionEnabled(), exercise::setSecondCorrectionEnabled);
        exercise.setFeedbackSuggestionModule(dto.feedbackSuggestionModule());
        exercise.setGradingInstructions(dto.gradingInstructions());
        exercise.setExampleSolution(dto.exampleSolution());
    }

    /**
     * Replaces the grading criteria of the given exercise according to PUT semantics.
     */
    private void updateGradingCriteria(UpdateTextExerciseDTO dto, TextExercise exercise) {
        if (dto.gradingCriteria() == null || dto.gradingCriteria().isEmpty()) {
            clearInitializedCollection(exercise.getGradingCriteria());
            return;
        }

        Set<GradingCriterion> managedCriteria = exercise.ensureGradingCriteriaSet();

        Map<Long, GradingCriterion> existingById = managedCriteria.stream().filter(gc -> gc.getId() != null)
                .collect(Collectors.toMap(GradingCriterion::getId, gc -> gc, (a, b) -> a));

        Set<GradingCriterion> updated = dto.gradingCriteria().stream().map(gcDto -> resolveGradingCriterion(gcDto, existingById, exercise)).collect(Collectors.toSet());

        managedCriteria.clear();
        managedCriteria.addAll(updated);
    }

    /**
     * Resolves a single grading criterion from a DTO, reusing an existing managed criterion if available.
     */
    private GradingCriterion resolveGradingCriterion(de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO gcDto, Map<Long, GradingCriterion> existingById,
            TextExercise exercise) {
        if (gcDto.id() != null) {
            GradingCriterion existing = existingById.get(gcDto.id());
            if (existing != null) {
                gcDto.applyTo(existing);
                return existing;
            }
        }
        GradingCriterion newCriterion = gcDto.toEntity();
        newCriterion.setExercise(exercise);
        return newCriterion;
    }

    /**
     * Replaces the competency links of the given exercise according to PUT semantics.
     */
    private void updateCompetencyLinks(UpdateTextExerciseDTO dto, TextExercise exercise) {
        boolean hasLinks = dto.competencyLinks() != null && !dto.competencyLinks().isEmpty();
        if (!hasLinks) {
            clearInitializedCollection(exercise.getCompetencyLinks());
            return;
        }
        CompetencyApi api = competencyApi.orElseThrow(() -> new BadRequestAlertException("Competency links require Atlas to be enabled.", "CourseCompetency", "atlasDisabled"));

        Set<CompetencyExerciseLink> managedLinks = exercise.ensureCompetencyLinksSet();
        Map<Long, CompetencyExerciseLink> existingByCompetencyId = managedLinks.stream().filter(link -> link.getCompetency() != null && link.getCompetency().getId() != null)
                .collect(Collectors.toMap(link -> link.getCompetency().getId(), link -> link, (a, b) -> a));

        Long exerciseCourseId = Optional.ofNullable(exercise.getCourseViaExerciseGroupOrCourseMember()).map(c -> c.getId()).orElse(null);
        Set<CompetencyExerciseLink> updated = dto.competencyLinks().stream().map(linkDto -> {
            Long competencyId = linkDto.courseCompetencyDTO().id();
            CompetencyExerciseLink existing = existingByCompetencyId.get(competencyId);
            if (existing != null) {
                existing.setWeight(linkDto.weight());
                return existing;
            }
            Competency competencyRef = api.loadCompetency(competencyId);
            competencyRef.validateCompetencyBelongsToExerciseCourse(exerciseCourseId);
            return new CompetencyExerciseLink(competencyRef, exercise, linkDto.weight());
        }).collect(Collectors.toSet());

        managedLinks.clear();
        managedLinks.addAll(updated);
    }

    /**
     * Clears the given collection if it is initialized.
     */
    private static <T> void clearInitializedCollection(Set<T> set) {
        DTOHelper.clearIfInitialized(set);
    }

    /**
     * Applies DTO values to a new TextExercise entity for creation via PUT.
     * Sets courseId/exerciseGroupId as proxy objects so that
     * {@link CourseService#retrieveCourseOverExerciseGroupOrCourseId} can resolve them.
     */
    private void applyDtoToNewExercise(UpdateTextExerciseDTO dto, TextExercise exercise) {
        applyCommonFields(dto, exercise);
        exercise.setProblemStatement(dto.problemStatement());

        // Set course or exercise group reference
        if (dto.courseId() != null) {
            Course courseRef = new Course();
            courseRef.setId(dto.courseId());
            exercise.setCourse(courseRef);
        }
        if (dto.exerciseGroupId() != null) {
            var exerciseGroup = new de.tum.cit.aet.artemis.exam.domain.ExerciseGroup();
            exerciseGroup.setId(dto.exerciseGroupId());
            exercise.setExerciseGroup(exerciseGroup);
        }
    }

    /**
     * Helper method to notify AtlasML about text exercise changes with consistent
     * error handling.
     */
    private void notifyAtlasML(TextExercise exercise, OperationTypeDTO operationType, String operationDescription) {
        atlasMLApi.ifPresent(api -> {
            try {
                api.saveExerciseWithCompetencies(exercise, operationType);
            }
            catch (Exception e) {
                log.warn("Failed to notify AtlasML about {}: {}", operationDescription, e.getMessage());
            }
        });
    }
}
