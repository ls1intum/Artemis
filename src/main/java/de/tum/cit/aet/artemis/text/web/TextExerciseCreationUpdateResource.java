package de.tum.cit.aet.artemis.text.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

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
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.CompetencyExerciseLinkFromEditorDTO;
import de.tum.cit.aet.artemis.text.dto.TextExerciseFromEditorDTO;
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

    private final Optional<SlideApi> slideApi;

    private final Optional<AtlasMLApi> atlasMLApi;

    private final TextExerciseRepository textExerciseRepository;

    private final UserRepository userRepository;

    private final ParticipationRepository participationRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    public TextExerciseCreationUpdateResource(TextExerciseRepository textExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, ParticipationRepository participationRepository, ExerciseService exerciseService,
            GroupNotificationScheduleService groupNotificationScheduleService, InstanceMessageSendService instanceMessageSendService, ChannelService channelService,
            ExerciseVersionService exerciseVersionService, Optional<AthenaApi> athenaApi, Optional<CompetencyProgressApi> competencyProgressApi, Optional<SlideApi> slideApi,
            Optional<AtlasMLApi> atlasMLApi, Optional<CourseCompetencyApi> courseCompetencyApi) {
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
        this.slideApi = slideApi;
        this.atlasMLApi = atlasMLApi;
        this.courseCompetencyApi = courseCompetencyApi;
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

        // Check that only allowed athena modules are used
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(textExercise, course, ENTITY_NAME), () -> textExercise.setFeedbackSuggestionModule(null));

        TextExercise result = textExerciseRepository.save(textExercise);

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
     * @param textExerciseDTO  the DTO containing the text exercise update data
     * @param notificationText about the text exercise update that should be
     *                             displayed for the
     *                             student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated
     *         textExercise, or
     *         with status 400 (Bad Request) if the textExercise is not valid, or
     *         with status 500 (Internal
     *         Server Error) if the textExercise couldn't be updated
     */
    @PutMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> updateTextExercise(@RequestBody @Valid TextExerciseFromEditorDTO textExerciseDTO,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update TextExercise with DTO: {}", textExerciseDTO);
        if (textExerciseDTO.id() == null) {
            throw new BadRequestAlertException("A text exercise update requires an ID", ENTITY_NAME, "idMissing");
        }

        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Important: use the original exercise for permission check and as the base for updates
        final TextExercise textExerciseBeforeUpdate = textExerciseRepository.findWithEagerCompetenciesAndCategoriesByIdElseThrow(textExerciseDTO.id());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExerciseBeforeUpdate, user);

        // Validate courseId and exerciseGroupId exclusivity from the DTO
        Long dtoCourseId = textExerciseDTO.courseId();
        Long dtoExerciseGroupId = textExerciseDTO.exerciseGroupId();

        // Check for invalid combination: both courseId and exerciseGroupId set
        if (dtoCourseId != null && dtoExerciseGroupId != null) {
            throw new BadRequestAlertException("A text exercise cannot have both a course and an exercise group", ENTITY_NAME, "invalidCourseAndExerciseGroup");
        }

        // Check for invalid combination: neither courseId nor exerciseGroupId set
        if (dtoCourseId == null && dtoExerciseGroupId == null) {
            throw new BadRequestAlertException("A text exercise must have either a course or an exercise group", ENTITY_NAME, "noCourseOrExerciseGroup");
        }

        // Check for conversion between course and exam exercise
        boolean existingIsCourseExercise = textExerciseBeforeUpdate.isCourseExercise();
        boolean dtoIsCourseExercise = dtoCourseId != null;
        if (existingIsCourseExercise != dtoIsCourseExercise) {
            throw new BadRequestAlertException("Cannot convert between course and exam exercise", ENTITY_NAME, "cannotConvert");
        }

        // Forbid changing the course the exercise belongs to
        Long existingCourseId = textExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId();

        // Determine the course ID from the DTO
        Long requestedCourseId;
        if (dtoCourseId != null) {
            requestedCourseId = dtoCourseId;
        }
        else if (dtoExerciseGroupId != null && textExerciseBeforeUpdate.getExerciseGroup() != null) {
            // For exam exercises, the course is derived from the exercise group
            requestedCourseId = textExerciseBeforeUpdate.getExerciseGroup().getExam().getCourse().getId();
        }
        else {
            // If neither is specified, use the existing course
            requestedCourseId = existingCourseId;
        }

        if (!Objects.equals(existingCourseId, requestedCourseId)) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        // Create a copy of the original exercise for comparison and notifications
        TextExercise originalExerciseCopy = copyFieldsForComparison(textExerciseBeforeUpdate);

        // Apply the DTO values to the managed entity
        textExerciseDTO.applyTo(textExerciseBeforeUpdate);

        // validates general settings: points, dates
        textExerciseBeforeUpdate.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        textExerciseBeforeUpdate.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Check that only allowed athena modules are used
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExerciseBeforeUpdate);
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(textExerciseBeforeUpdate, course, ENTITY_NAME),
                () -> textExerciseBeforeUpdate.setFeedbackSuggestionModule(null));
        // Changing Athena module after the due date has passed is not allowed
        athenaApi.ifPresent(api -> api.checkValidAthenaModuleChange(originalExerciseCopy, textExerciseBeforeUpdate, ENTITY_NAME));

        channelService.updateExerciseChannel(originalExerciseCopy, textExerciseBeforeUpdate);

        // Handle competency links
        // Use clear() and addAll() instead of setCompetencyLinks() to preserve the managed collection
        // This is important for orphan removal to work correctly
        if (textExerciseDTO.competencyLinks() != null) {
            Set<CompetencyExerciseLink> updatedLinks = updateCompetencyExerciseLinks(textExerciseBeforeUpdate, textExerciseDTO.competencyLinks(), course);
            textExerciseBeforeUpdate.getCompetencyLinks().clear();
            textExerciseBeforeUpdate.getCompetencyLinks().addAll(updatedLinks);
        }

        TextExercise updatedTextExercise = textExerciseRepository.save(textExerciseBeforeUpdate);

        exerciseService.logUpdate(updatedTextExercise, updatedTextExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(originalExerciseCopy, updatedTextExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedTextExercise, originalExerciseCopy.getDueDate());
        instanceMessageSendService.sendTextExerciseSchedule(updatedTextExercise.getId());
        // Fetch the exercise with example submissions for the check
        TextExercise exerciseWithExampleSubmissions = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(updatedTextExercise.getId());
        exerciseService.checkExampleSubmissions(exerciseWithExampleSubmissions);
        exerciseService.notifyAboutExerciseChanges(originalExerciseCopy, updatedTextExercise, notificationText);
        slideApi.ifPresent(api -> api.handleDueDateChange(originalExerciseCopy, updatedTextExercise));

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(originalExerciseCopy, Optional.of(updatedTextExercise)));

        exerciseVersionService.createExerciseVersion(updatedTextExercise);
        return ResponseEntity.ok(updatedTextExercise);
    }

    /**
     * Creates a shallow copy of the text exercise for comparison purposes.
     *
     * @param textExercise the text exercise to copy
     * @return a copy with the relevant fields
     */
    private TextExercise copyFieldsForComparison(TextExercise textExercise) {
        TextExercise copy = new TextExercise();
        copy.setId(textExercise.getId());
        copy.setTitle(textExercise.getTitle());
        copy.setMaxPoints(textExercise.getMaxPoints());
        copy.setBonusPoints(textExercise.getBonusPoints());
        copy.setReleaseDate(textExercise.getReleaseDate());
        copy.setStartDate(textExercise.getStartDate());
        copy.setDueDate(textExercise.getDueDate());
        copy.setAssessmentDueDate(textExercise.getAssessmentDueDate());
        copy.setExampleSolutionPublicationDate(textExercise.getExampleSolutionPublicationDate());
        copy.setProblemStatement(textExercise.getProblemStatement());
        copy.setGradingInstructions(textExercise.getGradingInstructions());
        copy.setExampleSolution(textExercise.getExampleSolution());
        copy.setFeedbackSuggestionModule(textExercise.getFeedbackSuggestionModule());
        copy.setIncludedInOverallScore(textExercise.getIncludedInOverallScore());
        copy.setCategories(textExercise.getCategories());
        copy.setCompetencyLinks(textExercise.getCompetencyLinks());
        if (!textExercise.isExamExercise()) {
            copy.setCourse(textExercise.getCourseViaExerciseGroupOrCourseMember());
        }
        copy.setExerciseGroup(textExercise.getExerciseGroup());
        return copy;
    }

    /**
     * Updates the competency exercise links based on the provided DTOs.
     *
     * @param textExercise the text exercise to update
     * @param competencies the competency link DTOs
     * @param course       the course the exercise belongs to
     * @return the updated set of competency exercise links
     */
    private Set<CompetencyExerciseLink> updateCompetencyExerciseLinks(TextExercise textExercise, Set<CompetencyExerciseLinkFromEditorDTO> competencies, Course course) {
        if (courseCompetencyApi.isEmpty()) {
            return Set.of();
        }
        CourseCompetencyApi api = this.courseCompetencyApi.get();
        Set<CompetencyExerciseLink> updatedLinks = new HashSet<>();
        Set<Long> competencyIds = competencies.stream().map(CompetencyExerciseLinkFromEditorDTO::competencyId).collect(Collectors.toSet());
        Set<Competency> foundCompetencies = api.findCourseCompetenciesByIdsAndCourseId(competencyIds, course.getId());
        for (CompetencyExerciseLinkFromEditorDTO dto : competencies) {
            Optional<Competency> matchingCompetency = foundCompetencies.stream().filter(c -> c.getId().equals(dto.competencyId())).findFirst();
            if (matchingCompetency.isPresent()) {
                CompetencyExerciseLink link = new CompetencyExerciseLink();
                link.setCompetency(matchingCompetency.get());
                link.setWeight(dto.weight());
                link.setExercise(textExercise);
                updatedLinks.add(link);
            }
            else {
                throw new EntityNotFoundException("Competency with id " + dto.competencyId() + " not found in course " + course.getId());
            }
        }
        return updatedLinks;
    }

    /**
     * PUT /text-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an
     * existing textExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param textExercise                                the text exercise to re-evaluate and update
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that
     *                                                        indicates whether the
     *                                                        associated feedback should
     *                                                        be deleted or not
     * @return the ResponseEntity with status 200 (OK) and with body the updated
     *         textExercise, or
     *         with status 400 (Bad Request) if the textExercise is not valid, or
     *         with status 409 (Conflict)
     *         if given exerciseId is not same as in the object of the request body,
     *         or with status 500
     *         (Internal Server Error) if the textExercise couldn't be updated
     */
    @PutMapping("text-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> reEvaluateAndUpdateTextExercise(@PathVariable long exerciseId, @RequestBody TextExercise textExercise,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate TextExercise : {}", textExercise);

        // check that the exercise exists for given id
        textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);

        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(exerciseId, textExercise);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExercise);

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        // Use the detached entity for re-evaluation (it has the grading criteria from the client)
        exerciseService.reEvaluateExercise(textExercise, deleteFeedbackAfterGradingInstructionUpdate);

        // Fetch the managed entity and merge grading criteria
        TextExercise managedExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(exerciseId);

        // Clear existing grading criteria and add the new ones
        managedExercise.getGradingCriteria().clear();
        if (textExercise.getGradingCriteria() != null) {
            for (GradingCriterion criterion : textExercise.getGradingCriteria()) {
                // Create new criteria linked to the managed exercise
                criterion.setExercise(managedExercise);
                managedExercise.getGradingCriteria().add(criterion);
            }
        }

        // Apply other fields from the detached entity to the managed one
        TextExerciseFromEditorDTO.of(textExercise).applyTo(managedExercise);

        TextExercise updatedTextExercise = textExerciseRepository.save(managedExercise);

        exerciseService.logUpdate(updatedTextExercise, updatedTextExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        instanceMessageSendService.sendTextExerciseSchedule(updatedTextExercise.getId());

        // Fetch with example submissions for the check
        TextExercise exerciseWithExampleSubmissions = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(updatedTextExercise.getId());
        exerciseService.checkExampleSubmissions(exerciseWithExampleSubmissions);

        exerciseVersionService.createExerciseVersion(updatedTextExercise);
        return ResponseEntity.ok(updatedTextExercise);
    }

    /**
     * Helper method to notify AtlasML about text exercise changes with consistent
     * error handling.
     *
     * @param exercise             the exercise to save
     * @param operationType        the operation type (UPDATE or DELETE)
     * @param operationDescription the description of the operation for logging
     *                                 purposes
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
