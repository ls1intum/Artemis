import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { BehaviorSubject, Observable, catchError, defer, distinctUntilChanged, from, map, of, switchMap, take, throwError } from 'rxjs';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { PageActivityService } from 'app/foundation/service/page-activity.service';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';

export type IrisAskUserQuizType = 'regular' | 'inClass';

/**
 * Request to (re-)activate the Iris panel for a given exercise, identified by a monotonically increasing
 * sequence number so that duplicate/older requests can be distinguished from the latest one.
 */
export interface IrisPanelActivationRequest {
    readonly sequence: number;
    readonly exerciseId: number;
}

/**
 * Coordinates the "ask user" quiz feature of Iris: tracks whether the feature is enabled, whether a quiz
 * is currently active/started for the current exercise, manages the quiz answer timer, and reacts to
 * chat pipe events and page (de)focus/run failures to keep that state consistent.
 */
@Injectable()
export class IrisAskUserService {
    private readonly askUserHttpService = inject(IrisAskUserHttpService);
    private readonly chatService = inject(IrisChatService);
    private readonly pageActivity = inject(PageActivityService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly activeQuizTypeState = new BehaviorSubject<ReadonlyMap<number, IrisAskUserQuizType>>(new Map());

    private readonly _enabled = signal(false);
    private readonly _quizActive = signal(false);
    private readonly _quizStarted = signal(false);
    private readonly _timerExpiresAt = signal<dayjs.Dayjs | undefined>(undefined);
    private readonly _timeLimit = signal(0);
    private readonly _showOnlyAskUserModeMessage = signal(false);
    private readonly _irisPanelActivationRequest = signal<IrisPanelActivationRequest | undefined>(undefined);

    readonly enabled = this._enabled.asReadonly();
    readonly quizActive = this._quizActive.asReadonly();
    readonly quizStarted = this._quizStarted.asReadonly();
    readonly timerExpiresAt = this._timerExpiresAt.asReadonly();
    readonly timeLimit = this._timeLimit.asReadonly();
    readonly showOnlyAskUserModeMessage = this._showOnlyAskUserModeMessage.asReadonly();
    readonly irisPanelActivationRequest = this._irisPanelActivationRequest.asReadonly();

    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly exerciseId = computed(() => this.exercise()?.id);

    readonly latestSubmissionHasPoints = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(false);
                }

                return this.askUserHttpService.latestSubmissionHasPoints(exerciseId).pipe(catchError(() => of(false)));
            }),
        ),
        { initialValue: false },
    );

    readonly activeQuizType = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((exerciseId) => {
                if (exerciseId === undefined) {
                    return of(undefined);
                }

                return this.activeQuizTypeForExercise(exerciseId);
            }),
        ),
        { initialValue: undefined },
    );

    readonly isAnyAskUserMode = computed(() => this.activeQuizType() !== undefined);

    /**
     * Enables the ask-user feature and starts listening to the events it depends on. No-op if already enabled.
     */
    activate(): void {
        if (this.enabled()) {
            return;
        }

        this._enabled.set(true);
        this.loadData();
    }

    /**
     * Disables the ask-user feature. No-op if already disabled.
     */
    deactivate(): void {
        if (!this.enabled()) {
            return;
        }

        this._enabled.set(false);
    }

    /**
     * Wires up the subscriptions the ask-user feature relies on (chat pipe events, page defocus, run failures,
     * and the chat service's stop-timer signal).
     */
    private loadData(): void {
        this.handleEvents();
        this.handleDefocus();
        this.handleRunFailures();

        this.chatService.stopTimer$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.stopAskUserTimer());
    }

    /**
     * Starts the answer timer for the current programming exercise's quiz session, if applicable.
     */
    private startAskUserTimer(): void {
        const exerciseId = this.currentProgrammingExerciseId();
        if (exerciseId === undefined) {
            return;
        }

        this.askUserHttpService
            .startTimer(exerciseId)
            .pipe(take(1))
            .subscribe({
                next: (response) => {
                    if (response.body?.timerExpiresAt === undefined) {
                        return;
                    }

                    this._timerExpiresAt.set(convertDateFromServer(response.body.timerExpiresAt));
                    this._timeLimit.set(response.body.timeLimit);
                },
                error: () => this.clearAskUserTimerState(),
            });
    }

    /**
     * Clears the local timer state and notifies the server to stop the timer for the current quiz session.
     */
    protected stopAskUserTimer(): void {
        const exerciseId = this.currentProgrammingExerciseId();
        this.clearAskUserTimerState();
        if (exerciseId !== undefined) {
            this.askUserHttpService
                .stopTimer(exerciseId)
                .pipe(take(1))
                .subscribe({ error: () => undefined });
        }
    }

    public clearAskUserTimerState(): void {
        this._timerExpiresAt.set(undefined);
        this._timeLimit.set(0);
    }

    /**
     * Resets the local quiz-active/started state, timer state, and the active quiz type for the current
     * exercise. No-op if there is nothing to reset.
     */
    private resetAskUserQuizState(): void {
        const exerciseId = this.exerciseId();
        const activeQuizType = exerciseId === undefined ? undefined : this.activeQuizTypeState.value.get(exerciseId);
        if (!this.quizActive() && !this.quizStarted() && activeQuizType === undefined) {
            return;
        }

        this._quizActive.set(false);
        this._quizStarted.set(false);
        this.clearAskUserTimerState();
        if (exerciseId !== undefined) {
            this.clearActiveQuizTypeForExercise(exerciseId);
        }
    }

    /**
     * Resets the quiz state after a build-with-points event, but only if the currently active quiz type
     * for the exercise is the regular (non-in-class) quiz.
     */
    private resetRegularAskUserQuizStateAfterBuildWithPoints(): void {
        const exerciseId = this.exerciseId();
        if (exerciseId === undefined || this.activeQuizTypeState.value.get(exerciseId) !== 'regular') {
            return;
        }

        this.resetAskUserQuizState();
    }

    /**
     * @returns the id of the current exercise if it is a programming exercise, otherwise undefined
     */
    private currentProgrammingExerciseId(): number | undefined {
        const exercise = this.exercise();
        return exercise?.type === ExerciseType.PROGRAMMING ? exercise.id : undefined;
    }

    /**
     * Subscribes to the chat service's latest pipe event stream and updates the local quiz/timer state
     * accordingly (e.g. quiz started, first/next question, quiz finished, build with points).
     */
    private handleEvents(): void {
        this.chatService
            .currentLatestEvent()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((event) => {
                const exerciseId = this.currentProgrammingExerciseId();
                if (exerciseId === undefined) {
                    return;
                }

                switch (event) {
                    case IrisPipeEvent.BUILD_WITH_POINTS:
                        this.resetRegularAskUserQuizStateAfterBuildWithPoints();
                        break;
                    case IrisPipeEvent.USER_STARTS_QUIZ:
                        this._quizStarted.set(true);
                        this.requestIrisPanelActivation(exerciseId);
                        break;
                    case IrisPipeEvent.FIRST_QUESTION:
                        this._quizActive.set(true);
                        this._quizStarted.set(true);
                        this.startAskUserTimer();
                        break;
                    case IrisPipeEvent.NEXT_QUESTION:
                        this.startAskUserTimer();
                        break;
                    case IrisPipeEvent.QUIZ_FINISHED:
                        this._quizActive.set(false);
                        this._quizStarted.set(false);
                        this.clearAskUserTimerState();
                        break;
                    default:
                        break;
                }
            });
    }

    /**
     * Requests activation of the Iris panel for the given exercise by publishing a new activation request
     * with an incremented sequence number so that repeated requests are always distinguishable.
     * @param exerciseId The unique identifier of the exercise
     */
    private requestIrisPanelActivation(exerciseId: number): void {
        const previousRequest = this._irisPanelActivationRequest();
        this._irisPanelActivationRequest.set({ sequence: (previousRequest?.sequence ?? 0) + 1, exerciseId });
    }

    /**
     * Subscribes to the page-leaving signal and ends the currently active quiz (locally and on the server)
     * if the user navigates away while a quiz is active and no answer is currently being awaited.
     */
    private handleDefocus(): void {
        this.pageActivity.pageLeaving$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            const exerciseId = this.currentProgrammingExerciseId();
            if (this.quizActive() && !this.chatService.awaitingAnswer() && exerciseId !== undefined) {
                this._quizActive.set(false);
                this._quizStarted.set(false);
                this.clearAskUserTimerState();
                this.askUserHttpService.registerDefocusForCurrentSession(exerciseId).subscribe({ error: () => undefined });
            }
        });
    }

    /**
     * Subscribes to the chat service's current run info and resets the ask-user quiz state whenever a run fails.
     */
    private handleRunFailures(): void {
        this.chatService
            .currentRunInfo()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((runInfo) => {
                if (runInfo?.state === IrisRunState.FAILED) {
                    this.resetAskUserQuizState();
                }
            });
    }

    /**
     * Emits the currently active quiz type (if any) for the given exercise, and re-emits whenever it changes.
     * @param exerciseId The unique identifier of the exercise
     */
    activeQuizTypeForExercise(exerciseId: number): Observable<IrisAskUserQuizType | undefined> {
        return this.activeQuizTypeState.pipe(
            map((activeQuizTypes) => activeQuizTypes.get(exerciseId)),
            distinctUntilChanged(),
        );
    }

    /**
     * Marks the given quiz type as active for the given exercise.
     * @param exerciseId The unique identifier of the exercise
     * @param quizType The quiz type to mark as active
     */
    setActiveQuizTypeForExercise(exerciseId: number, quizType: IrisAskUserQuizType): void {
        const activeQuizTypes = new Map(this.activeQuizTypeState.value);
        activeQuizTypes.set(exerciseId, quizType);
        this.activeQuizTypeState.next(activeQuizTypes);
    }

    /**
     * Clears the active quiz type for the given exercise. If a quizType is provided, the active type is only
     * cleared when it matches, so that a stale/superseded request cannot clear a newer active quiz.
     * @param exerciseId The unique identifier of the exercise
     * @param quizType Only clear if the currently active type matches this value
     */
    clearActiveQuizTypeForExercise(exerciseId: number, quizType?: IrisAskUserQuizType): void {
        const activeQuizTypes = new Map(this.activeQuizTypeState.value);
        if (quizType && activeQuizTypes.get(exerciseId) !== quizType) {
            return;
        }

        activeQuizTypes.delete(exerciseId);
        this.activeQuizTypeState.next(activeQuizTypes);
    }

    /**
     * starts the editor-controlled in-class quiz for the exercise for a student
     * @param exerciseId The unique identifier of the exercise
     */
    startInClassQuiz(exerciseId: number): Observable<void> {
        return defer(() => {
            this.setActiveQuizTypeForExercise(exerciseId, 'inClass');
            return from(this.chatService.clearChat()).pipe(
                switchMap(() => this.askUserHttpService.startInClassQuiz(exerciseId)),
                map(() => undefined),
                catchError(() => {
                    this.clearActiveQuizTypeForExercise(exerciseId, 'inClass');
                    return throwError(() => new Error(IrisErrorMessageKey.START_ASK_USER_FAILED));
                }),
            );
        });
    }

    /**
     * starts the quiz for the exercise for a student
     * @param exerciseId The unique identifier of the exercise
     */
    startQuiz(exerciseId: number): Observable<void> {
        return defer(() => {
            this.setActiveQuizTypeForExercise(exerciseId, 'regular');
            return from(this.chatService.clearChat()).pipe(
                switchMap(() => this.askUserHttpService.startQuiz(exerciseId)),
                map(() => undefined),
                catchError(() => {
                    this.clearActiveQuizTypeForExercise(exerciseId, 'regular');
                    return throwError(() => new Error(IrisErrorMessageKey.START_ASK_USER_FAILED));
                }),
            );
        });
    }
}
