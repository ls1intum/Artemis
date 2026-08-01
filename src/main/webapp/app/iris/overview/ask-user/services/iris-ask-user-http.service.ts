import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, defer, distinctUntilChanged, from, map, switchMap, throwError } from 'rxjs';
import { IrisQuizTimerDTO } from 'app/iris/shared/entities/iris-quiz-timer-dto.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';

export type IrisAskUserQuizType = 'regular' | 'inClass';

@Injectable({ providedIn: 'root' })
export class IrisAskUserHttpService {
    private http = inject(HttpClient);
    private irisChatService = inject(IrisChatService);
    private readonly activeQuizTypeState = new BehaviorSubject<ReadonlyMap<number, IrisAskUserQuizType>>(new Map());

    public resourceUrl = 'api/iris/programming-exercises';

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
     * Checks whether the in-class quiz been started already.
     *
     * @param exerciseId The unique identifier of the exercise
     */
    currentStartedInClassQuizForExercise(exerciseId: number): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/in-class/is-quiz-started`);
    }

    /**
     * Checks whether the quiz been started already.
     *
     * @param exerciseId The unique identifier of the exercise
     */
    currentStartedQuizForExercise(exerciseId: number): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/is-quiz-started`);
    }

    /**
     * registers a tab-defocus event while quiz is active and ends the quiz
     * @param exerciseId The unique identifier of the exercise
     */
    registerDefocusForCurrentSession(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/defocus`, {}, { observe: 'response' });
    }

    /**
     * starts the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    startTimer(exerciseId: number): Observable<HttpResponse<IrisQuizTimerDTO>> {
        return this.http.patch<IrisQuizTimerDTO>(`${this.resourceUrl}/${exerciseId}/ask-user/start-timer`, {}, { observe: 'response' });
    }

    /**
     * starts the instructor-controlled in-class quiz for the exercise for a student
     * @param exerciseId The unique identifier of the exercise
     */
    startInClassQuiz(exerciseId: number): Observable<void> {
        return defer(() => {
            this.setActiveQuizTypeForExercise(exerciseId, 'inClass');
            return from(this.irisChatService.clearChat()).pipe(
                switchMap(() => this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/in-class/start`, null, { observe: 'response' })),
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
            return from(this.irisChatService.clearChat()).pipe(
                switchMap(() => this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/start`, null, { observe: 'response' })),
                map(() => undefined),
                catchError(() => {
                    this.clearActiveQuizTypeForExercise(exerciseId, 'regular');
                    return throwError(() => new Error(IrisErrorMessageKey.START_ASK_USER_FAILED));
                }),
            );
        });
    }

    latestSubmissionHasPoints(exerciseId: number): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/latest-submission-has-points`);
    }

    isQuizAlreadyDone(exerciseId: number, inClass: boolean): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/completed`, { params: { inClass } });
    }

    /**
     * stops the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    stopTimer(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/stop-timer`, {}, { observe: 'response' });
    }
}
