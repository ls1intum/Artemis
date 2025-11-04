package de.tum.cit.aet.artemis.text.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

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

import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
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

    private final Optional<IrisSettingsApi> irisSettingsApi;

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
            Optional<IrisSettingsApi> irisSettingsApi, Optional<SlideApi> slideApi,

            Optional<AtlasMLApi> atlasMLApi) {
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
        this.irisSettingsApi = irisSettingsApi;
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

        // Check that only allowed athena modules are used
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(textExercise, course, ENTITY_NAME), () -> textExercise.setFeedbackSuggestionModule(null));

        TextExercise result = exerciseService.saveWithCompetencyLinks(textExercise, textExerciseRepository::save);

        channelService.createExerciseChannel(result, Optional.ofNullable(textExercise.getChannelName()));
        instanceMessageSendService.sendTextExerciseSchedule(result.getId());
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(textExercise);
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(result));

        irisSettingsApi.ifPresent(api -> api.setEnabledForExerciseByCategories(result, new HashSet<>()));

        // Notify AtlasML about the new text exercise
        notifyAtlasML(result, OperationTypeDTO.UPDATE, "text exercise creation");

        exerciseVersionService.createExerciseVersion(result);

        return ResponseEntity.created(new URI("/api/text/text-exercises/" + result.getId())).body(result);
    }

    /**
     * PUT /text-exercises : Updates an existing textExercise.
     *
     * @param textExercise     the textExercise to update
     * @param notificationText about the text exercise update that should be
     *                             displayed for the
     *                             student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated
     *         textExercise, or
     *         with status 400 (Bad Request) if the textExercise is not valid, or
     *         with status 500 (Internal
     *         Server Error) if the textExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> updateTextExercise(@RequestBody TextExercise textExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update TextExercise : {}", textExercise);
        if (textExercise.getId() == null) {
            return createTextExercise(textExercise);
        }
        // validates general settings: points, dates
        textExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        textExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Check that the user is authorized to update the exercise
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Important: use the original exercise for permission check
        final TextExercise textExerciseBeforeUpdate = textExerciseRepository.findWithEagerCompetenciesAndCategoriesByIdElseThrow(textExercise.getId());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExerciseBeforeUpdate, user);

        // Forbid changing the course the exercise belongs to.
        if (!Objects.equals(textExerciseBeforeUpdate.getCourseViaExerciseGroupOrCourseMember().getId(), textExercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(textExercise, textExerciseBeforeUpdate, ENTITY_NAME);

        // Check that only allowed athena modules are used
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExerciseBeforeUpdate);
        athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(textExercise, course, ENTITY_NAME), () -> textExercise.setFeedbackSuggestionModule(null));
        // Changing Athena module after the due date has passed is not allowed
        athenaApi.ifPresent(api -> api.checkValidAthenaModuleChange(textExerciseBeforeUpdate, textExercise, ENTITY_NAME));

        channelService.updateExerciseChannel(textExerciseBeforeUpdate, textExercise);

        TextExercise updatedTextExercise = exerciseService.saveWithCompetencyLinks(textExercise, textExerciseRepository::save);

        exerciseService.logUpdate(updatedTextExercise, updatedTextExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseService.updatePointsInRelatedParticipantScores(textExerciseBeforeUpdate, updatedTextExercise);
        participationRepository.removeIndividualDueDatesIfBeforeDueDate(updatedTextExercise, textExerciseBeforeUpdate.getDueDate());
        instanceMessageSendService.sendTextExerciseSchedule(updatedTextExercise.getId());
        exerciseService.checkExampleSubmissions(updatedTextExercise);
        exerciseService.notifyAboutExerciseChanges(textExerciseBeforeUpdate, updatedTextExercise, notificationText);
        slideApi.ifPresent(api -> api.handleDueDateChange(textExerciseBeforeUpdate, updatedTextExercise));

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(textExerciseBeforeUpdate, Optional.of(textExercise)));

        irisSettingsApi.ifPresent(api -> api.setEnabledForExerciseByCategories(textExercise, textExerciseBeforeUpdate.getCategories()));
        exerciseVersionService.createExerciseVersion(updatedTextExercise);
        return ResponseEntity.ok(updatedTextExercise);
    }

    /**
     * PUT /text-exercises/{exerciseId}/re-evaluate : Re-evaluates and updates an
     * existing textExercise.
     *
     * @param exerciseId                                  of the exercise
     * @param textExercise                                the textExercise to
     *                                                        re-evaluate and update
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
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("text-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<TextExercise> reEvaluateAndUpdateTextExercise(@PathVariable long exerciseId, @RequestBody TextExercise textExercise,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) throws URISyntaxException {
        log.debug("REST request to re-evaluate TextExercise : {}", textExercise);

        // check that the exercise exists for given id
        textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);

        authCheckService.checkGivenExerciseIdSameForExerciseInRequestBodyElseThrow(exerciseId, textExercise);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(textExercise);

        // Check that the user is authorized to update the exercise
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        exerciseService.reEvaluateExercise(textExercise, deleteFeedbackAfterGradingInstructionUpdate);
        return updateTextExercise(textExercise, null);
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
