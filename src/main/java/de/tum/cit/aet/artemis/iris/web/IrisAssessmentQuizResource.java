package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.session.IrisExerciseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for managing client requests while the assessment quiz is running.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/programming-exercises/")
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
     * PATCH programming-exercises/{exerciseId}/assessment-quiz/start: Activates prompting mode in the current Iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 404 (Not Found)} if no session exists
     */
    @PatchMapping("{exerciseId}/assessment-quiz/start")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> startPromptingModeForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.startPromptingModeForCurrentSession(programmingExercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH programming-exercises/{exerciseId}/assessment-quiz/in-class/start-current-session: Activates in-class prompting mode in the current Iris session for the programming
     * exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 409 (Conflict)} if the in-class quiz timer is not active anymore
     */
    @PatchMapping("{exerciseId}/assessment-quiz/in-class/start-current-session")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> startInClassPromptingModeForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.startInClassPromptingModeForCurrentSession(programmingExercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH programming-exercises/{exerciseId}/assessment-quiz/defocus: Signals a tab-defocus event while prompting.
     * The quiz is stopped and the corresponding verdict and reasoning is saved. Also, the tab_defocus event is sent to te pipeline.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)}
     */
    @PatchMapping("{exerciseId}/assessment-quiz/defocus")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> registerDefocusForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.registerDefocusForCurrentSession(exercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH programming-exercises/{exerciseId}/assessment-quiz/start-timer: Starts the timer for the current prompting session.
     * The current time is saved.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the corresponding timer data
     */
    @PatchMapping("{exerciseId}/assessment-quiz/start-timer")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisQuizTimerDTO> startTimerForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisExerciseChatSessionService.startTimerForCurrentSession(exercise, user));
    }

    /**
     * PATCH programming-exercises/{exerciseId}/assessment-quiz/in-class/start: Starts the instructor-controlled in-class quiz window.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the corresponding timer data
     */
    @PatchMapping("{exerciseId}/assessment-quiz/in-class/start")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<IrisQuizTimerDTO> startInClassQuiz(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);

        return ResponseEntity.ok(irisExerciseChatSessionService.startInClassQuiz(exercise));
    }

    /**
     * GET programming-exercises/{exerciseId}/assessment-quiz/in-class: Gets the active instructor-controlled in-class quiz window.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the timer data, or {@code null} if no timer is active
     */
    @GetMapping("{exerciseId}/assessment-quiz/in-class")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisQuizTimerDTO> getActiveInClassQuiz(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);

        return ResponseEntity.ok(irisExerciseChatSessionService.getActiveInClassQuiz(exercise));
    }

    /**
     * GET programming-exercises/{exerciseId}/assessment-quiz/latest-submission-has-points: Checks whether the current user's latest submission has points.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and a boolean body
     */
    @GetMapping("{exerciseId}/assessment-quiz/latest-submission-has-points")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Boolean> latestSubmissionHasPoints(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisExerciseChatSessionService.latestSubmissionHasPoints(exercise, user));
    }

    /**
     * GET programming-exercises/{exerciseId}/assessment-quiz/completed: Checks whether the current user already completed the quiz.
     *
     * @param exerciseId of the exercise
     * @param inClass    whether to check the in-class assessment verdict instead of the regular assessment verdict
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and a boolean body
     */
    @GetMapping("{exerciseId}/assessment-quiz/completed")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Boolean> isQuizAlreadyDone(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean inClass) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisExerciseChatSessionService.isQuizAlreadyDone(exercise, user, inClass));
    }

    /**
     * PATCH programming-exercises/{exerciseId}/assessment-quiz/stop-timer: Stops the timer for the current prompting session.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)}
     */
    @PatchMapping("{exerciseId}/assessment-quiz/stop-timer")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> stopTimerForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.PROMPT_USER, exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisExerciseChatSessionService.stopTimerForCurrentSession(exercise, user);
        return ResponseEntity.ok().build();
    }

    private static ProgrammingExercise validateExercise(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return exercise;
    }
}
