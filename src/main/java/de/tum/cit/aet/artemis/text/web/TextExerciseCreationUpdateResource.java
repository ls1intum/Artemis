package de.tum.cit.aet.artemis.text.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
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
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
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

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    public TextExerciseCreationUpdateResource(TextExerciseRepository textExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, ParticipationRepository participationRepository, ExerciseService exerciseService,
            GroupNotificationScheduleService groupNotificationScheduleService, InstanceMessageSendService instanceMessageSendService, ChannelService channelService,
            ExerciseVersionService exerciseVersionService, Optional<AthenaApi> athenaApi, Optional<CompetencyProgressApi> competencyProgressApi,
            Optional<CompetencyApi> competencyApi, Optional<SlideApi> slideApi, Optional<AtlasMLApi> atlasMLApi, CompetencyExerciseLinkService competencyExerciseLinkService) {
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
        this.competencyExerciseLinkService = competencyExerciseLinkService;
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

        var competencyLinks = competencyExerciseLinkService.extractCompetencyLinksForCreation(textExercise);
        TextExercise savedExercise = textExerciseRepository.save(textExercise);
        if (!competencyLinks.isEmpty()) {
            competencyExerciseLinkService.addCompetencyLinksForCreation(savedExercise, competencyLinks);
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
        // For course exercises, verify the courseId matches; for exam exercises, courseId is null (exerciseGroupId is used instead)
        if (updateTextExerciseDTO.courseId() != null && !Objects.equals(originalExercise.getCourseViaExerciseGroupOrCourseMember().getId(), updateTextExerciseDTO.courseId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        ZonedDateTime oldDueDate = originalExercise.getDueDate();
        ZonedDateTime oldAssessmentDueDate = originalExercise.getAssessmentDueDate();
        ZonedDateTime oldReleaseDate = originalExercise.getReleaseDate();
        Double oldMaxPoints = originalExercise.getMaxPoints();
        Double oldBonusPoints = originalExercise.getBonusPoints();
        String oldProblemStatement = originalExercise.getProblemStatement();
        String oldFeedbackSuggestionModule = originalExercise.getFeedbackSuggestionModule();
        // Capture original competency IDs before update() mutates the entity (L1 cache)
        Set<Long> originalCompetencyIds = originalExercise.getCompetencyLinks().stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

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

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, persistedExercise));

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

        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Apply DTO changes BEFORE re-evaluation so that updated grading criteria take effect.
        TextExercise exerciseForReevaluation = update(updateTextExerciseDTO, existingExercise);
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(exerciseForReevaluation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(exerciseForReevaluation, deleteFeedbackAfterGradingInstructionUpdate);

        // Save directly instead of delegating to updateTextExercise() to avoid double side effects.
        TextExercise savedExercise = textExerciseRepository.save(exerciseForReevaluation);

        // Apply all post-save side effects once with the captured originals.
        exerciseService.logUpdate(savedExercise, savedExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(originalMaxPoints, originalBonusPoints, savedExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedExercise, originalDueDate);
        instanceMessageSendService.sendTextExerciseSchedule(savedExercise.getId());
        exerciseService.notifyAboutExerciseChanges(originalReleaseDate, originalAssessmentDueDate, originalProblemStatement, savedExercise, null);
        slideApi.ifPresent(api -> api.handleDueDateChange(originalDueDate, savedExercise));
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, savedExercise));
        exerciseVersionService.createExerciseVersion(savedExercise);

        return ResponseEntity.ok(savedExercise);
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
        exercise.setTitle(dto.title());
        exercise.validateTitle();
        exercise.setShortName(dto.shortName());
        // problemStatement: null -> empty string
        String newProblemStatement = dto.problemStatement() == null ? "" : dto.problemStatement();
        exercise.setProblemStatement(newProblemStatement);

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

        // validates general settings: points, dates, etc.
        exercise.validateGeneralSettings();

        // Only set boolean values if they are explicitly provided (not null)
        if (dto.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(dto.allowComplaintsForAutomaticAssessments());
        }
        if (dto.allowFeedbackRequests() != null) {
            exercise.setAllowFeedbackRequests(dto.allowFeedbackRequests());
        }
        if (dto.presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(dto.presentationScoreEnabled());
        }
        if (dto.secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(dto.secondCorrectionEnabled());
        }
        exercise.setFeedbackSuggestionModule(dto.feedbackSuggestionModule());
        exercise.setGradingInstructions(dto.gradingInstructions());

        // TextExercise specific fields
        exercise.setExampleSolution(dto.exampleSolution());

        updateGradingCriteria(dto, exercise);
        competencyExerciseLinkService.updateCompetencyLinks(dto, exercise);

        return exercise;
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
     */
    private static <T> void clearInitializedCollection(Set<T> set) {
        if (set != null && Hibernate.isInitialized(set)) {
            set.clear();
        }
    }

    /**
     * Applies DTO values to a new TextExercise entity for creation via PUT.
     * Sets courseId/exerciseGroupId as proxy objects so that
     * {@link CourseService#retrieveCourseOverExerciseGroupOrCourseId} can resolve them.
     */
    private void applyDtoToNewExercise(UpdateTextExerciseDTO dto, TextExercise exercise) {
        exercise.setTitle(dto.title());
        exercise.setShortName(dto.shortName());
        exercise.setProblemStatement(dto.problemStatement());
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
        exercise.setFeedbackSuggestionModule(dto.feedbackSuggestionModule());
        exercise.setGradingInstructions(dto.gradingInstructions());
        exercise.setExampleSolution(dto.exampleSolution());
        if (dto.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(dto.allowComplaintsForAutomaticAssessments());
        }
        if (dto.allowFeedbackRequests() != null) {
            exercise.setAllowFeedbackRequests(dto.allowFeedbackRequests());
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
