import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { catchError, of, switchMap, take } from 'rxjs';

import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { QuizTimerBarComponent } from 'app/iris/overview/ask-user/quiz-timer-bar/quiz-timer-bar.component';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/foundation/service/alert.service';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { TumUiButtonDirective } from '@tumaet/ui-angular';

/**
 * Button that lets a student start the editor-controlled in-class ask-user quiz for an exercise, reflecting
 * whether the quiz is currently active, already completed, or startable, and displaying its answer timer.
 */
@Component({
    selector: 'jhi-start-in-class-quiz-button',
    templateUrl: './start-in-class-quiz-button.component.html',
    imports: [FeatureToggleDirective, ArtemisTranslatePipe, QuizTimerBarComponent, TumUiButtonDirective, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisStartInClassQuizButtonComponent {
    private readonly irisChatService = inject(IrisChatService);
    private readonly askUserService = inject(IrisAskUserService);
    private readonly askUserHttpService = inject(IrisAskUserHttpService);
    private readonly assessmentReviewService = inject(IrisAssessmentReviewHttpService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exercise = input.required<Exercise>();
    readonly participation = input<StudentParticipation>();
    readonly smallButtons = input.required<boolean>();
    readonly hideLabelMobile = input(false);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faBrain = faBrain;

    protected readonly isInClassAskUserMode = signal(false);
    private readonly latestSubmissionHasPointsFromEvent = signal(false);
    private readonly quizCompletedAfterCurrentRun = signal(false);

    private readonly exerciseId = computed(() => this.exercise().id);

    private readonly runInfo = toSignal(this.irisChatService.currentRunInfo(), { initialValue: undefined });

    private readonly quizAlreadyDoneFromServer = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.askUserHttpService.isQuizAlreadyDone(exerciseId, true).pipe(catchError(() => of(false)));
            }),
        ),
        { initialValue: false },
    );
    protected readonly quizAlreadyDone = computed(() => this.quizAlreadyDoneFromServer() || this.quizCompletedAfterCurrentRun());
    protected readonly buttonSeverity = computed(() => (this.quizAlreadyDone() && !this.showQuizActive() ? 'success' : 'primary'));
    protected readonly buttonSize = computed(() => (this.smallButtons() ? 'small' : 'default'));

    protected readonly availableInClassQuiz = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(undefined);
                }

                return this.assessmentReviewService.availableInClassQuizForExercise(exerciseId).pipe(catchError(() => of(undefined)));
            }),
        ),
        { initialValue: undefined },
    );

    protected readonly hasSubmissionWithPoints = computed(() => this.askUserService.latestSubmissionHasPoints() || this.latestSubmissionHasPointsFromEvent());

    protected readonly showQuizActive = computed(
        () =>
            this.askUserService.activeQuizType() === 'inClass' ||
            this.isInClassAskUserMode() ||
            (this.askUserService.activeQuizType() === 'regular' && this.hasSubmissionWithPoints() && !this.quizAlreadyDone()),
    );

    protected readonly canBeStarted = computed(
        () => this.hasSubmissionWithPoints() && !this.quizAlreadyDone() && !this.askUserService.isAnyAskUserMode() && !this.showQuizActive(),
    );

    private readonly showNoSubmission = computed(() => this.availableInClassQuiz() !== undefined && !this.showQuizActive() && !this.quizAlreadyDone() && !this.canBeStarted());

    protected readonly buttonLabel = computed(() => {
        if (this.showQuizActive()) {
            return 'artemisApp.exerciseActions.askUser.currently';
        } else if (this.quizAlreadyDone()) {
            return 'artemisApp.exerciseActions.askUser.finished';
        } else if (this.canBeStarted()) {
            return 'artemisApp.iris.assessmentInClassQuiz.start';
        } else {
            return 'artemisApp.exerciseActions.askUser.noSubmission';
        }
    });

    /**
     * Resets local quiz state on exercise change, fetches the currently started in-class/regular quiz state
     * for the exercise, reacts to chat pipe events (submission with points, quiz finished), and clears the
     * in-class quiz state whenever the current run fails.
     */
    constructor() {
        toObservable(this.exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                this.latestSubmissionHasPointsFromEvent.set(false);
                this.quizCompletedAfterCurrentRun.set(false);
            });

        // Initial fetch if ask-user mode is currently active
        toObservable(this.exerciseId)
            .pipe(
                switchMap((exerciseId) => {
                    if (exerciseId === undefined) {
                        return of(false);
                    }

                    return this.askUserHttpService.currentStartedInClassQuizForExercise(exerciseId).pipe(catchError(() => of(false)));
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((isStarted) => {
                this.isInClassAskUserMode.set(isStarted);
                const exerciseId = this.exerciseId();
                if (exerciseId !== undefined && isStarted) {
                    this.askUserService.setActiveQuizTypeForExercise(exerciseId, 'inClass');
                }
            });

        toObservable(this.exerciseId)
            .pipe(
                switchMap((exerciseId) => {
                    if (exerciseId === undefined) {
                        return of(false);
                    }

                    return this.askUserHttpService.currentStartedQuizForExercise(exerciseId).pipe(catchError(() => of(false)));
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((isStarted) => {
                const exerciseId = this.exerciseId();
                if (exerciseId !== undefined && isStarted) {
                    this.askUserService.setActiveQuizTypeForExercise(exerciseId, 'regular');
                }
            });

        this.irisChatService
            .currentLatestEvent()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((event) => {
                switch (event) {
                    case IrisPipeEvent.BUILD_WITH_POINTS:
                        if (this.showNoSubmission()) {
                            this.latestSubmissionHasPointsFromEvent.set(true);
                        }
                        break;
                    case IrisPipeEvent.QUIZ_FINISHED: {
                        if (this.askUserService.activeQuizType() === 'inClass' || this.isInClassAskUserMode()) {
                            this.quizCompletedAfterCurrentRun.set(true);
                        }

                        this.isInClassAskUserMode.set(false);
                        const exerciseId = this.exerciseId();
                        if (exerciseId !== undefined) {
                            this.askUserService.clearActiveQuizTypeForExercise(exerciseId);
                        }
                        break;
                    }
                    default:
                        break;
                }
            });

        effect(() => {
            const exerciseId = this.exerciseId();
            if (this.runInfo()?.state === IrisRunState.FAILED && exerciseId !== undefined) {
                untracked(() => {
                    if (this.askUserService.activeQuizType() === 'inClass' || this.isInClassAskUserMode()) {
                        this.isInClassAskUserMode.set(false);
                        this.askUserService.clearActiveQuizTypeForExercise(exerciseId, 'inClass');
                    }
                });
            }
        });
    }

    /**
     * Starts the in-class quiz for the current exercise, if it can currently be started, rolling back the
     * local state and showing an alert if the request fails.
     */
    protected startInClassQuiz(): void {
        if (!this.canBeStarted()) {
            return;
        }

        const exerciseId = this.exerciseId();
        if (exerciseId === undefined) {
            return;
        }

        this.isInClassAskUserMode.set(true);
        this.quizCompletedAfterCurrentRun.set(false);

        this.askUserService
            .startInClassQuiz(exerciseId)
            .pipe(take(1))
            .subscribe({
                error: () => {
                    this.isInClassAskUserMode.set(false);
                    this.askUserService.clearActiveQuizTypeForExercise(exerciseId, 'inClass');
                    this.alertService.error(IrisErrorMessageKey.START_ASK_USER_FAILED);
                },
            });
    }

    /**
     * Clears the locally cached available in-class quiz for the current exercise once its timer has expired.
     */
    protected handleTimerExpired(): void {
        const exerciseId = this.exerciseId();
        if (exerciseId !== undefined) {
            this.assessmentReviewService.clearActiveInClassQuiz(exerciseId);
        }
    }
}
