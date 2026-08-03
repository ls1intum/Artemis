package de.tum.cit.aet.artemis.iris.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.session.IrisAskUserService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * REST controller for managing client requests while the assessment quiz is running.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/programming-exercises/")
public class IrisAskUserResource {

    private final IrisAskUserService irisAskUserService;

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final ProgrammingExerciseRepository exerciseRepository;

    protected IrisAskUserResource(IrisAskUserService irisAskUserService, UserRepository userRepository, ProgrammingExerciseRepository exerciseRepository,
            IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService) {
        this.irisAskUserService = irisAskUserService;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * PATCH programming-exercises/{exerciseId}/ask-user/start: Activates ask-user mode in the current Iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 404 (Not Found)} if no session exists
     */
    @PatchMapping("{exerciseId}/ask-user/start")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> startQuizForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisAskUserService.startQuizForCurrentSession(programmingExercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH programming-exercises/{exerciseId}/ask-user/in-class/start: Activates in-class quiz in the current Iris session for the programming
     * exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} or {@code 409 (Conflict)} if the in-class quiz timer is not active anymore
     */
    @PatchMapping("{exerciseId}/ask-user/in-class/start")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> startInClassQuizForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        ProgrammingExercise programmingExercise = validateExercise(exercise);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisAskUserService.startInClassQuizForCurrentSession(programmingExercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH programming-exercises/{exerciseId}/ask-user/defocus: Signals a tab-defocus event while ask-user.
     * The quiz is stopped and the corresponding verdict and reasoning is saved. Also, the tab_defocus event is sent to te pipeline.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)}
     */
    @PatchMapping("{exerciseId}/ask-user/defocus")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> registerDefocusForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisAskUserService.registerDefocusForCurrentSession(exercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH programming-exercises/{exerciseId}/ask-user/start-timer: Starts the timer for the current ask-user session.
     * The current time is saved.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the corresponding timer data ( undefined timerExpired if no timer was started, because quiz was
     *         just stopped by something else)
     */
    @PatchMapping("{exerciseId}/ask-user/start-timer")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<IrisQuizTimerDTO> startTimerForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisAskUserService.startTimerForCurrentSession(exercise, user));
    }

    /**
     * GET programming-exercises/{exerciseId}/ask-user/latest-submission-has-points: Checks whether the current user's latest submission has points.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and a boolean body
     */
    @GetMapping("{exerciseId}/ask-user/latest-submission-has-points")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Boolean> latestSubmissionHasPoints(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisAskUserService.hasLatestSubmissionWithPointsBeforeDueDateIfExists(exercise, user));
    }

    /**
     * GET programming-exercises/{exerciseId}/ask-user/completed: Checks whether the current user already completed the quiz.
     *
     * @param exerciseId of the exercise
     * @param inClass    whether to check the in-class assessment verdict instead of the regular assessment verdict
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and a boolean body
     */
    @GetMapping("{exerciseId}/ask-user/completed")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Boolean> isQuizAlreadyDone(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean inClass) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisAskUserService.isQuizAlreadyDone(exercise, user, inClass));
    }

    /**
     * PATCH programming-exercises/{exerciseId}/ask-user/stop-timer: Stops the timer for the current ask-user session.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)}
     */
    @PatchMapping("{exerciseId}/ask-user/stop-timer")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> stopTimerForCurrentSession(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        irisAskUserService.stopTimerForCurrentSession(exercise, user);
        return ResponseEntity.ok().build();
    }

    /**
     * GET programming-exercises/{exerciseId}/ask-user/is-quiz-started: Checks whether the quiz been started already.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and a boolean body
     */
    @GetMapping("{exerciseId}/ask-user/is-quiz-started")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Boolean> isQuizStartedForExercise(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisAskUserService.isQuizStarted(exercise, user, false));
    }

    /**
     * GET programming-exercises/{exerciseId}/ask-user/in-class/is-quiz-started: Checks whether the in-class quiz been started already.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and a boolean body
     */
    @GetMapping("{exerciseId}/ask-user/in-class/is-quiz-started")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Boolean> isInClassQuizStartedForExercise(@PathVariable Long exerciseId) {
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        return ResponseEntity.ok(irisAskUserService.isQuizStarted(exercise, user, true));

    }

    private static ProgrammingExercise validateExercise(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        return exercise;
    }
}
