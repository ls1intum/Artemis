package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for managing client requests while the assessment quiz is running.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/assessment-quiz")
public class IrisAssessmentQuizResource {

    private final IrisExerciseChatSessionService irisExerciseChatSessionService;

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final ProgrammingExerciseRepository exerciseRepository;

    protected IrisAssessmentQuizResource(IrisExerciseChatSessionService irisExerciseChatSessionService, UserRepository userRepository,
            ProgrammingExerciseRepository exerciseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService) {
        this.irisExerciseChatSessionService = irisExerciseChatSessionService;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * PATCH assessment-quiz/{exerciseId}/defocus: Signals a tab-defocus event while prompting.
     * The quiz is stopped and the corresponding verdict and reasoning is saved. Also, the tab_defocus event is sent to te pipeline.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)}
     */
    @PatchMapping("{exerciseId}/defocus")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> registerDefocusForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.registerDefocusForCurrentSession(exercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH assessment-quiz/{exerciseId}/time-ran-out: Starts the timer for the current prompting session.
     * The current time is saved.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the corresponding timer data
     */
    @PatchMapping("{exerciseId}/start-timer")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisQuizTimerDTO> startTimerForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisExerciseChatSessionService.startTimerForCurrentSession(exercise, user));
    }

    /**
     * PATCH assessment-quiz/{exerciseId}/time-ran-out: Stops the timer for the current prompting session.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)}
     */
    @PatchMapping("{exerciseId}/stop-timer")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> stopTimerForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.stopTimerForCurrentSession(exercise, user);
        return ResponseEntity.ok().build();
    }
}
