import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { catchError, of, switchMap, take } from 'rxjs';

import { Exercise, hasDueDatePassed } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/foundation/service/alert.service';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';

@Component({
    selector: 'jhi-start-quiz-button',
    templateUrl: './start-quiz-button.component.html',
    imports: [FeatureToggleDirective, ArtemisTranslatePipe, FaIconComponent, TumUiButtonDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisStartQuizButtonComponent {
    private readonly irisChatService = inject(IrisChatService);
    private readonly askUserService = inject(IrisAskUserService);
    private readonly askUserHttpService = inject(IrisAskUserHttpService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exercise = input.required<Exercise>();
    readonly participation = input<StudentParticipation>();
    readonly smallButtons = input.required<boolean>();
    readonly hideLabelMobile = input(false);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faBrain = faBrain;

    private readonly exerciseId = computed(() => this.exercise().id);
    protected readonly afterDueDate = computed(() => hasDueDatePassed(this.exercise()));

    protected readonly isAskUserMode = signal(false);
    private readonly quizCompletedAfterCurrentRun = signal(false);
    private readonly quizAlreadyDoneFromServerInvalidated = signal(false);
    private readonly latestSubmissionHasPointsFromEvent = signal(false);

    private readonly runInfo = toSignal(this.irisChatService.currentRunInfo(), { initialValue: undefined });

    private readonly quizAlreadyDoneFromServer = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.askUserHttpService.isQuizAlreadyDone(exerciseId, false).pipe(catchError(() => of(false)));
            }),
        ),
        { initialValue: false },
    );

    protected readonly quizAlreadyDone = computed(() => (this.quizAlreadyDoneFromServer() && !this.quizAlreadyDoneFromServerInvalidated()) || this.quizCompletedAfterCurrentRun());
    protected readonly buttonSeverity = computed(() => (this.quizAlreadyDone() && !this.showQuizActive() ? 'success' : 'primary'));
    protected readonly buttonSize = computed(() => (this.smallButtons() ? 'small' : 'default'));

    protected readonly showQuizActive = computed(
        () =>
            this.askUserService.activeQuizType() === 'regular' ||
            this.isAskUserMode() ||
            (this.askUserService.activeQuizType() === 'inClass' && this.hasSubmissionWithPoints() && !this.quizAlreadyDone()),
    );

    protected readonly hasSubmissionWithPoints = computed(() => this.askUserService.latestSubmissionHasPoints() || this.latestSubmissionHasPointsFromEvent());

    protected readonly canBeStarted = computed(
        () => this.hasSubmissionWithPoints() && !this.quizAlreadyDone() && !this.askUserService.isAnyAskUserMode() && !this.showQuizActive(),
    );

    protected readonly buttonLabel = computed(() => {
        if (this.showQuizActive()) {
            return 'artemisApp.exerciseActions.askUser.currently';
        } else if (this.quizAlreadyDone()) {
            return 'artemisApp.exerciseActions.askUser.finished';
        } else if (this.canBeStarted()) {
            return 'artemisApp.exerciseActions.askUser.start';
        } else {
            return 'artemisApp.exerciseActions.askUser.noSubmission';
        }
    });

    constructor() {
        toObservable(this.exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                this.quizCompletedAfterCurrentRun.set(false);
                this.quizAlreadyDoneFromServerInvalidated.set(false);
                this.latestSubmissionHasPointsFromEvent.set(false);
            });

        // Initial fetch if ask-user mode is currently active
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
                this.isAskUserMode.set(isStarted);
                const exerciseId = this.exerciseId();
                if (exerciseId !== undefined && isStarted) {
                    this.askUserService.setActiveQuizTypeForExercise(exerciseId, 'regular');
                }
            });

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
                const exerciseId = this.exerciseId();
                if (exerciseId !== undefined && isStarted) {
                    this.askUserService.setActiveQuizTypeForExercise(exerciseId, 'inClass');
                }
            });

        this.irisChatService
            .currentLatestEvent()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((event) => {
                switch (event) {
                    case IrisPipeEvent.BUILD_WITH_POINTS:
                        this.resetActiveRegularQuiz();
                        this.latestSubmissionHasPointsFromEvent.set(true);
                        this.quizCompletedAfterCurrentRun.set(false);
                        this.quizAlreadyDoneFromServerInvalidated.set(true);
                        break;
                    case IrisPipeEvent.QUIZ_FINISHED: {
                        if (this.askUserService.activeQuizType() === 'regular' || this.isAskUserMode()) {
                            this.quizCompletedAfterCurrentRun.set(true);
                        }

                        this.isAskUserMode.set(false);
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
            if (this.runInfo()?.state === IrisRunState.FAILED) {
                untracked(() => this.resetActiveRegularQuiz());
            }
        });
    }

    private resetActiveRegularQuiz(): void {
        const exerciseId = this.exerciseId();
        if (exerciseId !== undefined && (this.askUserService.activeQuizType() === 'regular' || this.isAskUserMode())) {
            this.isAskUserMode.set(false);
            this.askUserService.clearActiveQuizTypeForExercise(exerciseId, 'regular');
        }
    }

    protected startQuiz(): void {
        const exerciseId = this.exerciseId();
        if (!this.canBeStarted() || exerciseId === undefined) {
            return;
        }

        this.isAskUserMode.set(true);
        this.quizCompletedAfterCurrentRun.set(false);

        this.askUserService
            .startQuiz(exerciseId)
            .pipe(take(1))
            .subscribe({
                error: () => {
                    this.isAskUserMode.set(false);
                    this.askUserService.clearActiveQuizTypeForExercise(exerciseId, 'regular');
                    this.alertService.error(IrisErrorMessageKey.START_ASK_USER_FAILED);
                },
            });
    }
}
