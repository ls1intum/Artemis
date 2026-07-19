package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IrisPipeEventDTO;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * REST controller for prompting mode actions and state.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisPromptingModeResource {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final UserRepository userRepository;

    private final IrisSettingsService irisSettingsService;

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    private final AuthorizationCheckService authorizationCheckService;

    protected IrisPromptingModeResource(ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, UserRepository userRepository,
            IrisSettingsService irisSettingsService, IrisExerciseChatSessionService irisExerciseChatSessionService, AuthorizationCheckService authorizationCheckService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.userRepository = userRepository;
        this.irisSettingsService = irisSettingsService;
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * PATCH programming-exercises/{exerciseId}/sessions/current/prompting: Activates prompting mode in the current Iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 404 (Not Found)} if no session exists
     */
    @PatchMapping("programming-exercises/{exerciseId}/sessions/current/prompting")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> startPromptingModeForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.startPromptingModeForCurrentSession(programmingExercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH programming-exercises/{exerciseId}/sessions/current/prompting/in-class: Activates in-class prompting mode in the current Iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 409 (Conflict)} if the in-class quiz timer is not active anymore
     */
    @PatchMapping("programming-exercises/{exerciseId}/sessions/current/prompting/in-class")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> startInClassPromptingModeForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.startInClassPromptingModeForCurrentSession(programmingExercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * GET participations/{participationId}/latest-event: Gets the latest Iris pipeline event for the assessment of a participation.
     *
     * @param participationId of the participation
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the latest event, or {@code null} if no assessment or event exists
     */
    @GetMapping("participations/{participationId}/latest-event")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisPipeEventDTO> getLastEvent(@PathVariable Long participationId) {
        var participation = programmingExerciseStudentParticipationRepository.findById(participationId).orElseThrow();
        if (!authorizationCheckService.isAtLeastStudentForExercise(participation.getExercise())) {
            throw new AccessForbiddenException();
        }
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, participation.getExercise());

        var assessment = participation.getIrisAssessment();
        if (assessment == null) {
            return ResponseEntity.ok(null);
        }

        return ResponseEntity.ok(new IrisPipeEventDTO(assessment.getLastEvent()));
    }

    private static ProgrammingExercise validateExercise(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return exercise;
    }
}
