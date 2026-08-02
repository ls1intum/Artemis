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

@Injectable({ providedIn: 'root' })
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

    readonly enabled = this._enabled.asReadonly();
    readonly quizActive = this._quizActive.asReadonly();
    readonly quizStarted = this._quizStarted.asReadonly();
    readonly timerExpiresAt = this._timerExpiresAt.asReadonly();
    readonly timeLimit = this._timeLimit.asReadonly();
    readonly showOnlyAskUserModeMessage = this._showOnlyAskUserModeMessage.asReadonly();

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

    activate(): void {
        if (this.enabled()) {
            return;
        }

        this._enabled.set(true);
        this.loadData();
    }

    deactivate(): void {
        if (!this.enabled()) {
            return;
        }

        this._enabled.set(false);
    }

    private loadData(): void {
        this.handleEvents();
        this.handleDefocus();
        this.handleRunFailures();

        this.chatService.stopTimer$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.stopAskUserTimer());
    }

    private startAskUserTimer(): void {
        const exerciseId = this.currentProgrammingExerciseId();
        if (exerciseId === undefined) {
            return;
        }

        this.askUserHttpService
            .startTimer(exerciseId)
            .pipe(take(1))
            .subscribe((response) => {
                if (response.body) {
                    this._timerExpiresAt.set(convertDateFromServer(response.body.timerExpiresAt));
                    this._timeLimit.set(response.body.timeLimit);
                } else {
                    throw new Error(IrisErrorMessageKey.START_ASK_USER_FAILED);
                }
            });
    }

    protected stopAskUserTimer(): void {
        const exerciseId = this.currentProgrammingExerciseId();
        this.clearAskUserTimerState();
        if (exerciseId !== undefined) {
            this.askUserHttpService.stopTimer(exerciseId).pipe(take(1)).subscribe();
        }
    }

    public clearAskUserTimerState(): void {
        this._timerExpiresAt.set(undefined);
        this._timeLimit.set(0);
    }

    private resetAskUserQuizStateAfterFailure(): void {
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

    private currentProgrammingExerciseId(): number | undefined {
        const exercise = this.exercise();
        return exercise?.type === ExerciseType.PROGRAMMING ? exercise.id : undefined;
    }

    private handleEvents(): void {
        this.chatService
            .currentLatestEvent()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((event) => {
                if (this.currentProgrammingExerciseId() === undefined) {
                    return;
                }

                switch (event) {
                    case IrisPipeEvent.USER_STARTS_QUIZ:
                        this._quizStarted.set(true);
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

    private handleDefocus(): void {
        this.pageActivity.pageLeaving$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            const exerciseId = this.currentProgrammingExerciseId();
            if (this.quizActive() && exerciseId !== undefined) {
                this._quizActive.set(false);
                this._quizStarted.set(false);
                this.clearAskUserTimerState();
                this.askUserHttpService.registerDefocusForCurrentSession(exerciseId).subscribe();
            }
        });
    }

    private handleRunFailures(): void {
        this.chatService
            .currentRunInfo()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((runInfo) => {
                if (runInfo?.state === IrisRunState.FAILED) {
                    this.resetAskUserQuizStateAfterFailure();
                }
            });
    }

    activeQuizTypeForExercise(exerciseId: number): Observable<IrisAskUserQuizType | undefined> {
        return this.activeQuizTypeState.pipe(
            map((activeQuizTypes) => activeQuizTypes.get(exerciseId)),
            distinctUntilChanged(),
        );
    }

    setActiveQuizTypeForExercise(exerciseId: number, quizType: IrisAskUserQuizType): void {
        const activeQuizTypes = new Map(this.activeQuizTypeState.value);
        activeQuizTypes.set(exerciseId, quizType);
        this.activeQuizTypeState.next(activeQuizTypes);
    }

    clearActiveQuizTypeForExercise(exerciseId: number, quizType?: IrisAskUserQuizType): void {
        const activeQuizTypes = new Map(this.activeQuizTypeState.value);
        if (quizType && activeQuizTypes.get(exerciseId) !== quizType) {
            return;
        }

        activeQuizTypes.delete(exerciseId);
        this.activeQuizTypeState.next(activeQuizTypes);
    }

    /**
     * starts the instructor-controlled in-class quiz for the exercise for a student
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
