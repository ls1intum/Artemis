import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { catchError, merge, of, switchMap, take } from 'rxjs';
import { map } from 'rxjs/operators';

import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { IrisAssessmentQuizService } from 'app/iris/overview/services/iris-assessment-quiz.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { QuizTimerBarComponent } from 'app/iris/overview/understanding-assessment/quiz-timer-bar/quiz-timer-bar.component';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { WebsocketService } from 'app/foundation/service/websocket.service';

@Component({
    selector: 'jhi-start-in-class-quiz-button',
    templateUrl: './start-in-class-quiz-button.component.html',
    imports: [ExerciseActionButtonComponent, FeatureToggleDirective, ArtemisTranslatePipe, QuizTimerBarComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisStartInClassQuizButtonComponent {
    private readonly irisChatService = inject(IrisChatService);
    private readonly assessmentQuizService = inject(IrisAssessmentQuizService);
    private readonly websocketService = inject(WebsocketService);

    readonly exercise = input.required<Exercise>();
    readonly participation = input<StudentParticipation>();
    readonly smallButtons = input.required<boolean>();
    readonly hideLabelMobile = input(false);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faBrain = faBrain;

    protected readonly isInClassPromptingMode = signal(false);

    private readonly exerciseId = computed(() => this.exercise().id);
    private readonly latestEvent = toSignal(this.irisChatService.currentLatestEvent(), { initialValue: undefined });

    private readonly latestSubmissionHasPoints = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.assessmentQuizService.latestSubmissionHasPoints(exerciseId).pipe(catchError(() => of(false)));
            }),
        ),
        { initialValue: false },
    );

    private readonly quizAlreadyDoneFromServer = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.assessmentQuizService.isQuizAlreadyDone(exerciseId, true).pipe(catchError(() => of(false)));
            }),
        ),
        { initialValue: false },
    );

    protected readonly quizAlreadyDone = computed(
        () => this.quizAlreadyDoneFromServer() || (this.isInClassPromptingMode() && this.latestEvent() === IrisPipeEvent.PROMPTING_FINISHED),
    );

    protected readonly activeInClassQuiz = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(undefined);
                }

                return merge(
                    this.assessmentQuizService.getActiveInClassQuiz(exerciseId).pipe(
                        map((response) => response.body ?? undefined),
                        catchError(() => of(undefined)),
                    ),
                    this.assessmentQuizService.currentInClassQuizForExercise(exerciseId),
                );
            }),
        ),
        { initialValue: undefined },
    );

    protected readonly resetStartedInClassQuizEffect = effect(() => {
        const exerciseId = this.exerciseId();
        if (exerciseId !== undefined && this.isInClassPromptingMode() && this.latestEvent() === IrisPipeEvent.PROMPTING_FINISHED) {
            this.assessmentQuizService.setInClassPromptingModeStarted(exerciseId, false);
        }
    });

    protected readonly inClassQuizStartedEffect = effect((onCleanup) => {
        const exerciseId = this.exerciseId();

        if (exerciseId === undefined) {
            return;
        }

        const websocketTopic = `/topic/iris/programming-exercises/${exerciseId}/assessment-quiz/in-class/start`;
        const websocketSubscription = this.websocketService
            .subscribe<void>(websocketTopic)
            .pipe(
                switchMap(() =>
                    this.assessmentQuizService.getActiveInClassQuiz(exerciseId).pipe(
                        catchError(() => {
                            this.assessmentQuizService.clearActiveInClassQuiz(exerciseId);
                            return of(undefined);
                        }),
                    ),
                ),
            )
            .subscribe();

        onCleanup(() => {
            websocketSubscription.unsubscribe();
        });
    });

    protected readonly canBeStarted = computed(() => this.latestSubmissionHasPoints() && !this.quizAlreadyDone() && !this.isInClassPromptingMode());

    protected readonly buttonLabel = computed(() => {
        if (this.quizAlreadyDone()) {
            return 'artemisApp.exerciseActions.prompting.finished';
        } else if (this.isInClassPromptingMode()) {
            return 'artemisApp.exerciseActions.prompting.currently';
        } else if (!this.latestSubmissionHasPoints()) {
            return 'artemisApp.exerciseActions.prompting.noSubmission';
        } else {
            return 'artemisApp.iris.assessmentInClassQuiz.start';
        }
    });

    protected startInClassQuiz(): void {
        if (!this.canBeStarted()) {
            return;
        }

        const exerciseId = this.exerciseId();
        if (exerciseId === undefined) {
            return;
        }

        this.isInClassPromptingMode.set(true);
        this.assessmentQuizService.setInClassPromptingModeStarted(exerciseId, true);

        this.irisChatService
            .startInClassPromptingMode(exerciseId)
            .pipe(take(1))
            .subscribe({
                error: () => {
                    this.isInClassPromptingMode.set(false);
                    this.assessmentQuizService.setInClassPromptingModeStarted(exerciseId, false);
                },
            });
    }

    protected handleTimerExpired(): void {
        const exerciseId = this.exerciseId();
        if (exerciseId !== undefined) {
            this.assessmentQuizService.clearActiveInClassQuiz(exerciseId);
        }
    }
}
